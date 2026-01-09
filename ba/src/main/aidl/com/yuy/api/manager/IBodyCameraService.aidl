package com.yuy.api.manager;

interface IBodyCameraService {
   //if BodyCamera is doing a mp4/mp3/wav audio recording task
   boolean isAudioRecording();
   //if BodyCamera is doing a mp4 video recording tasksdf
   boolean isVideoRecording();
   //starting a mp4/mp3/wav audio recording,please check first if it is audio disable /audio or video recording has started
   boolean startAudioRecording();
   //stopping a mp4/mp3/wav audio recording
   void stopAudioRecording();
   //starting a mp4 video recording,please check first if it is video disable /audio or video recording has started
   boolean startVideoRecording();
   //stopping a mp4 video recording
   void stopVideoRecording();
   //if audio module is available to BodyCamera
   boolean isAudioModuleEnabled();
    //making audio module available to BodyCamera sdfsdf
   void enableAudioModule();
   //stopping audio recording to mp4/mp3/wav/cluster intercom and making audio module not available to BodyCamera
   void disableAudioModule();
   //making camera module available to bodycamera
   void enableVideoModule();
   //if camera module is available to BodyCamera
   boolean isVideoModuleEnable();
   //stopping video recording to mp4/live video streaming/rtsp streaming and making camera module  not available for BodyCamera
   void disableVideoModule();
    //return size 2 list, package name in index 0, class name in index 1
   List<String> getFaceRecognitionPackageAndClass();
}
