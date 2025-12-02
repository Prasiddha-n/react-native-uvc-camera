import React from 'react';
import {
  NativeModules,
  findNodeHandle,
  requireNativeComponent,
  type NativeMethods,
} from 'react-native';
import type { UVCCameraProps } from './UVCCameraProps';
import type { PhotoFile } from './PhotoFile';

type CameraNativeModule = {
  openCamera(viewTag: number): Promise<void> | void;
  closeCamera(viewTag: number): Promise<void> | void;
  takePhoto(viewTag: number): Promise<PhotoFile>;
  updateAspectRatio(
    viewTag: number,
    width: number,
    height: number
  ): Promise<void> | void;
  setCameraBright(viewTag: number, value: number): Promise<void> | void;
  setContrast(viewTag: number, value: number): Promise<void> | void;
  setContast?(viewTag: number, value: number): Promise<void> | void;
  setSaturation(viewTag: number, value: number): Promise<void> | void;
  setSharpness(viewTag: number, value: number): Promise<void> | void;
  setZoom(viewTag: number, value: number): Promise<void> | void;
  setDefaultCameraVendorId(
    viewTag: number,
    value: number
  ): Promise<void> | void;
};

const CameraModule = NativeModules.UVCCameraView as
  | CameraNativeModule
  | undefined;
if (CameraModule == null) {
  console.error("Camera: Native Module 'UVCCameraView' was null!");
}

const ComponentName = 'UVCCameraView';

type NativeUVCCameraViewProps = UVCCameraProps;
const NativeUVCCameraView =
  requireNativeComponent<NativeUVCCameraViewProps>(ComponentName);
type RefType = React.Component<NativeUVCCameraViewProps> &
  Readonly<NativeMethods>;

export class UVCCamera extends React.PureComponent<UVCCameraProps> {
  private readonly ref: React.RefObject<RefType>;

  constructor(props: UVCCameraProps) {
    super(props);
    this.ref = React.createRef<RefType>();
  }

  private get handle(): number | null {
    return findNodeHandle(this.ref.current);
  }

  private get nativeModule(): CameraNativeModule {
    if (CameraModule == null) {
      throw new Error("Camera: Native Module 'UVCCameraView' was null!");
    }
    return CameraModule;
  }

  private get nativeHandle(): number {
    const handle = this.handle;
    if (handle == null) {
      throw new Error('Camera view is not attached');
    }
    return handle;
  }

  public render(): React.ReactNode {
    return <NativeUVCCameraView {...this.props} ref={this.ref} />;
  }

  public async openCamera(): Promise<void> {
    await this.nativeModule.openCamera(this.nativeHandle);
  }

  public async closeCamera(): Promise<void> {
    await this.nativeModule.closeCamera(this.nativeHandle);
  }

  public async takePhoto(): Promise<PhotoFile> {
    return await this.nativeModule.takePhoto(this.nativeHandle);
  }
  public async updateAspectRatio(width: number, height: number): Promise<void> {
    await this.nativeModule.updateAspectRatio(this.nativeHandle, width, height);
  }

  public async setCameraBright(value: number): Promise<void> {
    await this.nativeModule.setCameraBright(this.nativeHandle, value);
  }

  public async setCameraContrast(value: number): Promise<void> {
    const module = this.nativeModule;
    if (module.setContrast) {
      await module.setContrast(this.nativeHandle, value);
      return;
    }
    if (module.setContast) {
      await module.setContast(this.nativeHandle, value);
    }
  }

  public async setCameraSaturation(value: number): Promise<void> {
    await this.nativeModule.setSaturation(this.nativeHandle, value);
  }

  public async setCameraSharpness(value: number): Promise<void> {
    await this.nativeModule.setSharpness(this.nativeHandle, value);
  }

  public async setCameraZoom(value: number): Promise<void> {
    await this.nativeModule.setZoom(this.nativeHandle, value);
  }

  public async setDefaultCameraVendorId(value: number): Promise<void> {
    await this.nativeModule.setDefaultCameraVendorId(this.nativeHandle, value);
  }
}
