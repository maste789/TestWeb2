package com.sang.testweb;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.VideoView;
import org.webrtc.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {
    //root/sockettest/socketFiles/test_socket.js
    private static final String SIGNALING_URI = "https://joylab.one:3355";
    private static final String VIDEO_TRACK_ID = "video1";
    private static final String AUDIO_TRACK_ID = "audio1";
    private static final String LOCAL_STREAM_ID = "stream1";
    private static final String SDP_MID = "sdpMid";
    private static final String SDP_M_LINE_INDEX = "sdpMLineIndex";
    private static final String SDP = "sdp";
    private static final String CREATEOFFER = "createoffer";
    private static final String OFFER = "offer";
    private static final String ANSWER = "answer";
    private static final String CANDIDATE = "candidate";
    private static final String ROOMNAME = "roomname";
    private final peerConnectionObserver peerConnectionObserver = new peerConnectionObserver();

    PeerConnectionFactory peerConnectionFactory;
    //VideoSource localVideoSource;
    MediaStream localMediaStream;
    //VideoRenderer otherPeerRenderer;
    PeerConnection peerConnection;
    String roomname = "23451234";
    String Myplatform = "app";
    String platform="app";
    String web ="web";
    String app = "app";
    MediaConstraints mediaConstraints;
    EglBase rootEglBase;
    SurfaceViewRenderer localVideoView;
    SurfaceViewRenderer remoteVideoView;

    private Socket socket;
    private boolean createOffer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);

        initWebRTC();

    }

    // Web RTC 연결
    public void initWebRTC(){
        /*PeerConnectionFactory.initializeAndroidGlobals(this,true,true,true,null);
        peerConnectionFactory = new PeerConnectionFactory();*/
        rootEglBase=EglBase.create();
        //initialize PCF
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setFieldTrials("WebRTC-IntelVP8/Enabled")
                        .createInitializationOptions()
        );

        PeerConnectionFactory.Options options= new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory=PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
        VideoCapturer videoCapturer=createVideoCapturer();
        MediaConstraints constraints=new MediaConstraints();
        VideoSource videoSource=peerConnectionFactory.createVideoSource(false);
        //VideoTrack localVideoTrack= createVideoTrack(videoCapturer,videoSource);

        SurfaceTextureHelper textureHelper=SurfaceTextureHelper.create(Thread.currentThread().getName(),rootEglBase.getEglBaseContext());
        //VideoCapturerAndroid vc = VideoCapturerAndroid.create(VideoCapturerAndroid.getNameOfFrontFacingDevice());

        videoCapturer.initialize(textureHelper,this,videoSource.getCapturerObserver());
        //videoCapturer.startCapture(1024,720,30);//capture in HD
        //videoCapturer.startCapture(640,480,30);//capture in SD
        videoCapturer.startCapture(320,240,30);//capture in LD
        VideoTrack localVideoTrack=peerConnectionFactory.createVideoTrack("VIDEO1",videoSource);
        localVideoTrack.setEnabled(true);
        AudioSource audioSource=peerConnectionFactory.createAudioSource(constraints);
        AudioTrack localAudioTrack=peerConnectionFactory.createAudioTrack("AUDIO1",audioSource);
        localAudioTrack.setEnabled(true);



        remoteVideoView=findViewById(R.id.smallView);
        remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);

        localVideoView=findViewById(R.id.bigView);
        localVideoView.setMirror(true);
        localVideoView.init(rootEglBase.getEglBaseContext(),null);
        localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        localVideoTrack.addSink(localVideoView);
        Log.d("localVideo", localVideoTrack.toString());

        localMediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);

        localMediaStream.addTrack(localAudioTrack);
        localMediaStream.addTrack(localVideoTrack);

    }

    public void Closepeer(View button){
        if(peerConnection != null) {
            peerConnection.close();
            socket.close();
            peerConnection = null;
            createOffer = false;
        }
    }

    // 턴서버 연결과 소켓 연결 , 메인 스트림 연결 ( Connect 버튼 onclick )
    public void CreateTurn(View button){
        if (peerConnection != null)
            return;

        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer TrunSever = PeerConnection.IceServer.builder("turn:3.34.179.97:3478")
                                                .setUsername("qtg_acc")
                                                .setPassword("1234qwer1919")
                                                .createIceServer();
        PeerConnection.IceServer StrunSever = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                                                .createIceServer();
        iceServers.add(TrunSever);
        iceServers.add(StrunSever);
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(iceServers);
        mediaConstraints = new MediaConstraints();
        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));
        mediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT;
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                peerConnectionObserver);
        peerConnection.addStream(localMediaStream);
        try {
            socket = IO.socket(SIGNALING_URI);
            // 소켓 연결체크
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String socketID = socket.id();
                    Log.d("Test", "CreateTurn: "+SIGNALING_URI);
                    Log.d("SOCKET", "init: "+ socketID);
                    Log.d("SOCKET", "Connection success : " + socket.connected());
                    socket.emit("platform", roomname, Myplatform);
                }
            });
            //룸조인
            socket.emit("roomjoin", roomname);

            socket.on(CREATEOFFER, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    createOffer = true;
                    Log.d("CreateOffer" , String.valueOf(createOffer));
                    peerConnection.createOffer(sdpObserver, mediaConstraints);
                }
            }).on("platform", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String obj = args[0].toString();

                    platform = obj;
                    Log.d("platform",platform);
                }
            }).on(OFFER, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = (JSONObject) args[0];
                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER,
                                obj.getString(SDP));
                        Log.d("offer", obj.getString(SDP));

                        peerConnection.setRemoteDescription(sdpObserver, sdp);
                        peerConnection.createAnswer(sdpObserver, new MediaConstraints());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on(ANSWER, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = (JSONObject) args[0];
                        Log.d("answer", obj.getString(SDP));
                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.ANSWER,
                                obj.getString(SDP));
                        peerConnection.setRemoteDescription(sdpObserver, sdp);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on(CANDIDATE, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = (JSONObject) args[0];
                        Log.d("get candidate", obj.toString());
                        Log.d("platform", platform);
                        if(platform.equals("web") == true){
                            peerConnection.addIceCandidate(new IceCandidate(obj.getString(SDP_MID),
                                    obj.getInt(SDP_M_LINE_INDEX),
                                    obj.getString("candidate")));
                            Log.d("get candidate", obj.getString("candidate"));
                        }else {
                            peerConnection.addIceCandidate(new IceCandidate(obj.getString(SDP_MID),
                                    obj.getInt(SDP_M_LINE_INDEX),
                                    obj.getString(SDP)));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // Offer < - > response 소통방식은 SDP로 소통함.
    SdpObserver sdpObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            peerConnection.setLocalDescription(sdpObserver, sessionDescription);
            Log.d("Emit signalingState", peerConnection.signalingState().toString());

            try {
                JSONObject obj = new JSONObject();
                String type = sessionDescription.type.toString().toLowerCase();
                obj.put("type" , type );
                obj.put(SDP, sessionDescription.description);
                Log.d("Emit" , String.valueOf(createOffer));
                if (createOffer == true) {
                    Log.d("Emit" , "offer ");
                    socket.emit(OFFER, roomname, obj);
                } else {
                    Log.d("Emit" , "answer");
                    socket.emit(ANSWER, roomname,obj);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {
            Log.d("SetSuccess" , "성공");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.d("create Failed" , s);
        }

        @Override
        public void onSetFailure(String s) {
            Log.d("SetFailed" , s);
        }
    };

    private class peerConnectionObserver  implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d("RTCAPP", "onSignalingChange:" + signalingState.toString());
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d("RTCAPP", "onIceConnectionChange:" + iceConnectionState.toString());
            if(iceConnectionState.equals("FAILED") == true){

            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d("RTCAPP", "onIceGatheringChange:" + iceGatheringState.toString());
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            try {
                JSONObject obj = new JSONObject();
                obj.put(SDP_MID, iceCandidate.sdpMid);
                obj.put(SDP_M_LINE_INDEX, iceCandidate.sdpMLineIndex);
                obj.put(SDP, iceCandidate.sdp);
                socket.emit(CANDIDATE, roomname,obj);
                Log.d("send Candidate" , obj.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            //Log.d("mediaSteam", mediaStream.videoTracks.get(1).toString());
            mediaStream.videoTracks.get(0).addSink(remoteVideoView);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            int Len = mediaStream.videoTracks.size();
            //mediaStream.videoTracks.get(0).removeSink(remoteVideoView);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d("OndataChannel", dataChannel.state().toString());
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    };
    private VideoCapturer createVideoCapturer() {
        CameraEnumerator enumerator=new Camera1Enumerator(true);
        String [] devicenames=enumerator.getDeviceNames();
        for(String dn:devicenames)
        {
            if(enumerator.isFrontFacing(dn))
            {
                return enumerator.createCapturer(dn,null);
            }
        }
        //failed to get front facing cam
        for(String dn:devicenames)
        {
            if(!enumerator.isFrontFacing(dn))
            {
                return enumerator.createCapturer(dn,null);
            }
        }
        //failed to find both
        return null;
    }

}