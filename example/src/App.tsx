import { Text, View, StyleSheet, Button } from 'react-native';
import BlePeripheral from 'react-native-ble-peripheral';

export default function App() {
  const startAd = async () => {
    try {
      await BlePeripheral.addCharacteristic({
        serviceUUID: '12345678-1234-5678-1234-56789abcdef0',
        characteristicUUID: '12345678-1234-5678-1234-56789abcdef1',
        properties: ['read', 'write', 'notify'],
        permissions: ['readable', 'writeable'],
      });
      await BlePeripheral.startAdvertising(
        '12345678-1234-5678-1234-56789abcdef0',
        'BleExample'
      );
      console.log('Advertising started');
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <View style={styles.container}>
      <Text>BLE Peripheral Example</Text>
      <Button title="Start Advertising" onPress={startAd} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
