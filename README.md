# @vic1v1/react-native-ble-peripheral

a custom peripheral discovery package for react native

## Installation

```sh
npm install @vic1v1/react-native-ble-peripheral
```

## Usage

```tsx
import BlePeripheral from '@vic1v1/react-native-ble-peripheral';


// 1. Listen for state changes
const removeStateListener = BlePeripheral.onStateChanged((event) => {
  console.log('Bluetooth State:', event.state);
});

// 2. Add characteristics
await BlePeripheral.addCharacteristic({
  serviceUUID: '12345678-...',
  characteristicUUID: '87654321-...',
  properties: ['read', 'write', 'notify'],
  permissions: ['readable', 'writeable'],
});

// 3. Start advertising
await BlePeripheral.startAdvertising('12345678-...', 'MyBLEDevice');

// 4. Update values (notifies subscribers)
await BlePeripheral.updateCharacteristicValue('87654321-...', 'Hello BLE');

// 5. Listen for incoming writes
const removeWriteListener = BlePeripheral.onWriteReceived((event) => {
  console.log('Received write:', event.value, 'from', event.deviceAddress);
});

// Cleanup
// BlePeripheral.stopAdvertising();
// removeStateListener();
// removeWriteListener();
```

## API

### Methods

- `startAdvertising(serviceUUID, deviceName)`: Starts broadcasting the service.
- `stopAdvertising()`: Stops broadcasting.
- `addCharacteristic(config)`: Adds a GATT characteristic.
- `updateCharacteristicValue(uuid, value)`: Updates value and notifies subscribers.

### Events

- `onStateChanged(callback)`
- `onWriteReceived(callback)`
- `onSubscribed(callback)`
- `onUnsubscribed(callback)`
- `onAdvertisingStarted(callback)`
- `onError(callback)`



## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
