import { NativeEventEmitter, NativeModules } from 'react-native';
export const TBBluetooth = new NativeEventEmitter(NativeModules.TB);
