import { NativeEventEmitter, NativeModules } from 'react-native';
const TBBluetooth = new NativeEventEmitter(NativeModules.Bt);

export default TBBluetooth;
