import BlePeripheral from './NativeBlePeripheral';

export function multiply(a: number, b: number): number {
  return BlePeripheral.multiply(a, b);
}
