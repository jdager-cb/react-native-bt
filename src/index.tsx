import { NativeEventEmitter, NativeModules } from 'react-native';
const TBBluetooth= new NativeEventEmitter (NativeModules.TB);

export default TBBluetooth;
