import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-ble-peripheral' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const BlePeripheral = NativeModules.BlePeripheral
  ? NativeModules.BlePeripheral
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const eventEmitter = new NativeEventEmitter(BlePeripheral);

export interface CharacteristicConfig {
  serviceUUID: string;
  characteristicUUID: string;
  properties: (
    | 'read'
    | 'write'
    | 'writeWithoutResponse'
    | 'notify'
    | 'indicate'
  )[];
  permissions: ('readable' | 'writeable')[];
}

export interface WriteReceivedEvent {
  characteristicUUID: string;
  value: string;
  centralUUID?: string;
  deviceAddress?: string;
}

export interface StateChangedEvent {
  state:
    | 'poweredOn'
    | 'poweredOff'
    | 'unauthorized'
    | 'unsupported'
    | 'resetting'
    | 'unknown';
}

class BlePeripheralManager {
  private listeners: any[] = [];

  /**
   * Start advertising as a BLE peripheral
   * @param serviceUUID - The UUID of your service
   * @param deviceName - The name that will be broadcast
   */
  async startAdvertising(
    serviceUUID: string,
    deviceName: string
  ): Promise<void> {
    return BlePeripheral.startAdvertising(serviceUUID, deviceName);
  }

  /**
   * Stop advertising
   */
  async stopAdvertising(): Promise<void> {
    return BlePeripheral.stopAdvertising();
  }

  /**
   * Add a characteristic to the GATT server
   * @param config - Configuration for the characteristic
   */
  async addCharacteristic(config: CharacteristicConfig): Promise<void> {
    return BlePeripheral.addCharacteristic(
      config.serviceUUID,
      config.characteristicUUID,
      config.properties,
      config.permissions
    );
  }

  /**
   * Update a characteristic's value (will notify subscribed clients)
   * @param characteristicUUID - The UUID of the characteristic to update
   * @param value - The new value (will be encoded as UTF-8 string)
   */
  async updateCharacteristicValue(
    characteristicUUID: string,
    value: string
  ): Promise<void> {
    return BlePeripheral.updateCharacteristicValue(characteristicUUID, value);
  }

  /**
   * Listen for Bluetooth state changes
   * @param callback - Called when Bluetooth state changes
   * @returns Function to remove the listener
   */
  onStateChanged(callback: (event: StateChangedEvent) => void): () => void {
    const listener = eventEmitter.addListener(
      'BlePeripheralStateChanged',
      callback as any
    );
    this.listeners.push(listener);
    return () => listener.remove();
  }

  /**
   * Listen for incoming writes from central devices
   * @param callback - Called when data is written to a characteristic
   * @returns Function to remove the listener
   */
  onWriteReceived(callback: (event: WriteReceivedEvent) => void): () => void {
    const listener = eventEmitter.addListener(
      'BlePeripheralWriteReceived',
      callback as any
    );
    this.listeners.push(listener);
    return () => listener.remove();
  }

  /**
   * Listen for central devices subscribing to notifications
   * @param callback - Called when a central subscribes
   * @returns Function to remove the listener
   */
  onSubscribed(
    callback: (event: {
      characteristicUUID: string;
      centralUUID?: string;
      deviceAddress?: string;
    }) => void
  ): () => void {
    const listener = eventEmitter.addListener(
      'BlePeripheralSubscribed',
      callback as any
    );
    this.listeners.push(listener);
    return () => listener.remove();
  }

  /**
   * Listen for central devices unsubscribing from notifications
   * @param callback - Called when a central unsubscribes
   * @returns Function to remove the listener
   */
  onUnsubscribed(
    callback: (event: {
      characteristicUUID: string;
      centralUUID?: string;
      deviceAddress?: string;
    }) => void
  ): () => void {
    const listener = eventEmitter.addListener(
      'BlePeripheralUnsubscribed',
      callback as any
    );
    this.listeners.push(listener);
    return () => listener.remove();
  }

  /**
   * Listen for advertising start events
   * @param callback - Called when advertising starts successfully
   * @returns Function to remove the listener
   */
  onAdvertisingStarted(callback: () => void): () => void {
    const listener = eventEmitter.addListener(
      'BlePeripheralAdvertisingStarted',
      callback as any
    );
    this.listeners.push(listener);
    return () => listener.remove();
  }

  /**
   * Listen for errors
   * @param callback - Called when an error occurs
   * @returns Function to remove the listener
   */
  onError(
    callback: (event: { error: string; errorCode?: number }) => void
  ): () => void {
    const listener = eventEmitter.addListener(
      'BlePeripheralError',
      callback as any
    );
    this.listeners.push(listener);
    return () => listener.remove();
  }

  /**
   * Clean up all listeners
   */
  removeAllListeners(): void {
    this.listeners.forEach((listener) => listener.remove());
    this.listeners = [];
  }
}

export default new BlePeripheralManager();
