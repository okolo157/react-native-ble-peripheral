import { TurboModuleRegistry, type TurboModule } from 'react-native';

export interface Spec extends TurboModule {
  startAdvertising(serviceUUID: string, deviceName: string): Promise<Object>;
  stopAdvertising(): Promise<Object>;
  addCharacteristic(
    serviceUUID: string,
    characteristicUUID: string,
    properties: string[],
    permissions: string[]
  ): Promise<Object>;
  updateCharacteristicValue(
    characteristicUUID: string,
    value: string
  ): Promise<Object>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('BlePeripheral');
