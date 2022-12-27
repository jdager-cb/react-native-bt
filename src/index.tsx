import { NativeEventEmitter, NativeModules } from 'react-native';
const { Bt: TBBluetooth } = NativeModules;

export default new NativeEventEmitter(TBBluetooth);
