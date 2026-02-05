import CoreBluetooth
import React

@objc(BlePeripheral)
class BlePeripheral: RCTEventEmitter, CBPeripheralManagerDelegate {
    
    var peripheralManager: CBPeripheralManager!
    var service: CBMutableService?
    var characteristics: [String: CBMutableCharacteristic] = [:]
    private var hasListeners = false
    
    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }
    
    override func startObserving() {
        hasListeners = true
    }
    
    override func stopObserving() {
        hasListeners = false
    }
    
    @objc
    func startAdvertising(_ serviceUUID: String, 
                         deviceName: String,
                         resolver: @escaping RCTPromiseResolveBlock,
                         rejecter: @escaping RCTPromiseRejectBlock) {
        
        guard peripheralManager.state == .poweredOn else {
            rejecter("BLUETOOTH_OFF", "Bluetooth is not powered on", nil)
            return
        }
        
        // 1. Cleanup existing advertising and services
        peripheralManager.stopAdvertising()
        if let currentService = service {
            peripheralManager.remove(currentService)
        }
        
        // 2. Clear then Create new service
        let uuid = CBUUID(string: serviceUUID)
        let newService = CBMutableService(type: uuid, primary: true)
        
        // 3. Attach all characteristics that were queued via addCharacteristic
        newService.characteristics = Array(characteristics.values)
        self.service = newService
        
        // 4. Register with manager
        peripheralManager.add(newService)
        
        // 5. Start broadcasting
        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [uuid],
            CBAdvertisementDataLocalNameKey: deviceName
        ])
        
        resolver(["status": "advertising", "deviceName": deviceName])
    }
    
    @objc
    func stopAdvertising(_ resolver: @escaping RCTPromiseResolveBlock,
                        rejecter: @escaping RCTPromiseRejectBlock) {
        peripheralManager.stopAdvertising()
        
        if let currentService = service {
            peripheralManager.remove(currentService)
        }
        
        self.service = nil
        characteristics.removeAll()
        
        resolver(["status": "stopped"])
    }
    
    @objc
    func addCharacteristic(_ serviceUUID: String,
                          characteristicUUID: String,
                          properties: [String],
                          permissions: [String],
                          resolver: @escaping RCTPromiseResolveBlock,
                          rejecter: @escaping RCTPromiseRejectBlock) {
        
        let uuid = CBUUID(string: characteristicUUID)
        var props: CBCharacteristicProperties = []
        var perms: CBAttributePermissions = []
        
        // Parse properties
        for prop in properties {
            switch prop {
            case "read": props.insert(.read)
            case "write": props.insert(.write)
            case "writeWithoutResponse": props.insert(.writeWithoutResponse)
            case "notify": props.insert(.notify)
            case "indicate": props.insert(.indicate)
            default: break
            }
        }
        
        // Parse permissions
        for perm in permissions {
            switch perm {
            case "readable": perms.insert(.readable)
            case "writeable": perms.insert(.writeable)
            default: break
            }
        }
        
        // Create the characteristic
        let characteristic = CBMutableCharacteristic(
            type: uuid,
            properties: props,
            value: nil,
            permissions: perms
        )
        
        // Just store it in our dictionary for now.
        // It will be added to the service when startAdvertising is called.
        characteristics[characteristicUUID] = characteristic
        
        resolver(["status": "queued", "uuid": characteristicUUID])
    }
    
    @objc
    func updateCharacteristicValue(_ characteristicUUID: String,
                                   value: String,
                                   resolver: @escaping RCTPromiseResolveBlock,
                                   rejecter: @escaping RCTPromiseRejectBlock) {
        
        guard let characteristic = characteristics[characteristicUUID] else {
            rejecter("NOT_FOUND", "Characteristic not found", nil)
            return
        }
        
        guard let data = value.data(using: .utf8) else {
            rejecter("ENCODING_ERROR", "Failed to encode value", nil)
            return
        }
        
        let success = peripheralManager.updateValue(
            data,
            for: characteristic,
            onSubscribedCentrals: nil
        )
        
        if success {
            resolver(["success": true])
        } else {
            rejecter("UPDATE_FAILED", "Failed to update characteristic", nil)
        }
    }
    
    // MARK: - CBPeripheralManagerDelegate
    
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        guard hasListeners else { return }
        
        var state = ""
        switch peripheral.state {
        case .poweredOn: state = "poweredOn"
        case .poweredOff: state = "poweredOff"
        case .unauthorized: state = "unauthorized"
        case .unsupported: state = "unsupported"
        case .resetting: state = "resetting"
        case .unknown: state = "unknown"
        @unknown default: state = "unknown"
        }
        
        sendEvent(withName: "BlePeripheralStateChanged", body: ["state": state])
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, 
                          didAdd service: CBService, 
                          error: Error?) {
        guard hasListeners else { return }
        
        if let error = error {
            sendEvent(withName: "BlePeripheralError", body: [
                "error": error.localizedDescription
            ])
        } else {
            sendEvent(withName: "BlePeripheralServiceAdded", body: [
                "serviceUUID": service.uuid.uuidString
            ])
        }
    }
    
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, 
                                             error: Error?) {
        guard hasListeners else { return }
        
        if let error = error {
            sendEvent(withName: "BlePeripheralError", body: [
                "error": error.localizedDescription
            ])
        } else {
            sendEvent(withName: "BlePeripheralAdvertisingStarted", body: [:])
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, 
                          didReceiveWrite requests: [CBATTRequest]) {
        
        for request in requests {
            if let value = request.value,
               let stringValue = String(data: value, encoding: .utf8) {
                
                if hasListeners {
                    sendEvent(withName: "BlePeripheralWriteReceived", body: [
                        "characteristicUUID": request.characteristic.uuid.uuidString,
                        "value": stringValue,
                        "centralUUID": request.central.identifier.uuidString
                    ])
                }
            }
            
            peripheralManager.respond(to: request, withResult: .success)
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager,
                          didReceiveRead request: CBATTRequest) {
        
        if let characteristic = characteristics[request.characteristic.uuid.uuidString],
           let value = characteristic.value {
            request.value = value
            peripheralManager.respond(to: request, withResult: .success)
        } else {
            peripheralManager.respond(to: request, withResult: .attributeNotFound)
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager,
                          central: CBCentral,
                          didSubscribeTo characteristic: CBCharacteristic) {
        
        guard hasListeners else { return }
        
        sendEvent(withName: "BlePeripheralSubscribed", body: [
            "characteristicUUID": characteristic.uuid.uuidString,
            "centralUUID": central.identifier.uuidString
        ])
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager,
                          central: CBCentral,
                          didUnsubscribeFrom characteristic: CBCharacteristic) {
        
        guard hasListeners else { return }
        
        sendEvent(withName: "BlePeripheralUnsubscribed", body: [
            "characteristicUUID": characteristic.uuid.uuidString,
            "centralUUID": central.identifier.uuidString
        ])
    }
    
    // MARK: - React Native Event Emitter
    
    override func supportedEvents() -> [String]! {
        return [
            "BlePeripheralStateChanged",
            "BlePeripheralWriteReceived",
            "BlePeripheralReadReceived",
            "BlePeripheralSubscribed",
            "BlePeripheralUnsubscribed",
            "BlePeripheralServiceAdded",
            "BlePeripheralAdvertisingStarted",
            "BlePeripheralError"
        ]
    }
    
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    @objc
    override static func moduleName() -> String! {
        return "BlePeripheral"
    }
}
