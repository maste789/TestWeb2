package com.sang.testweb;

import androidx.appcompat.app.AppCompatActivity;


import android.content.Context;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.VideoView;

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
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URISyntaxException;
import java.util.ArrayList;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {
///root/sockettest/socketFiles/test_socket.js
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


    PeerConnectionFactory peerConnectionFactory;
    VideoSource localVideoSource;
    MediaStream localMediaStream;
    VideoRenderer otherPeerRenderer;
    PeerConnection peerConnection;
    String roomname = "12345";
    String Myplatform = "app";
    String platform="web";
    String web ="web";
    String app = "app";


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
        PeerConnectionFactory.initializeAndroidGlobals(this,true,true,true,null);
        peerConnectionFactory = new PeerConnectionFactory();
        

        VideoCapturerAndroid vc = VideoCapturerAndroid.create(VideoCapturerAndroid.getNameOfFrontFacingDevice());
        //
        localVideoSource = peerConnectionFactory.createVideoSource(vc, new MediaConstraints());
        VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID,localVideoSource);
        localVideoTrack.setEnabled(true);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID,audioSource);
        localAudioTrack.setEnabled(true);

        localMediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        localMediaStream.addTrack(localVideoTrack);
        localMediaStream.addTrack(localAudioTrack);
        // GL 서피스 뷰 배정.
        GLSurfaceView videoView = (GLSurfaceView) findViewById(R.id.view_Call);
        // 서피스뷰에 비디오 배정
        VideoRendererGui.setView(videoView , null);
        try{
            // 내것이 아닌 다른사람의 뷰 크기설정부분.
            otherPeerRenderer = VideoRendererGui.createGui(0,0,100,100,VideoRendererGui.ScalingType.SCALE_ASPECT_FILL , true);
            // 내화면 ( 왼쪽밑 작은 화면 )
            VideoRenderer renderer = VideoRendererGui.createGui(50,50,50,50,VideoRendererGui.ScalingType.SCALE_ASPECT_FILL,true);
            // 비디오트랙에 배정.
            localVideoTrack.addRenderer(renderer);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    // 턴서버 연결과 소켓 연결 , 메인 스트림 연결 ( Connect 버튼 onclick )
    public void CreateTurn(View button){
        if (peerConnection != null)
            return;
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("turn:3.34.179.97:3478","qtg_acc","1234qwer1919"));
        iceServers.add(new PeerConnection.IceServer("turn:15.164.229.255:3478","qtg_acc","1234qwer1919"));
        iceServers.add(new PeerConnection.IceServer( "turn:18.220.11.64:3478","qtg_acc","1234qwer1919"));
        peerConnection = peerConnectionFactory.createPeerConnection(
                iceServers,
                new MediaConstraints(),
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
                    MediaConstraints mediaConstraints = new MediaConstraints();
                    mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
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
                        String type = obj.optString("type");
                        Log.d("offer" , String.valueOf(createOffer));
                        Log.d("offer" , type);
                        String sdpset = obj.optString("sdp");
                        Log.d("answer" , type);
                        Log.d("answer" , sdpset);
                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER,
                                obj.getString(SDP));
                        Log.d("answer" , sdp.description);
                        Log.d("answer" , String.valueOf(sdp.type));
                        peerConnection.setRemoteDescription(sdpObserver, sdp);

                        if(platform.equals(web) == true){
                            Log.d("createanswer" , "오퍼받고 앤서생성직전");
                            peerConnection.createAnswer(new SdpObserver() {
                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {
                                    peerConnection.setLocalDescription(sdpObserver, sessionDescription);
                                    try {
                                        JSONObject obj = new JSONObject();
                                        String type = sessionDescription.type.toString().toLowerCase();
                                        obj.put("type", type);
                                        obj.put(SDP, sessionDescription.description);

                                            Log.d("Emit" , "answer");
                                            socket.emit(ANSWER, roomname,obj);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onSetSuccess() {

                                }

                                @Override
                                public void onCreateFailure(String s) {
                                    Log.d("create Failed" , s);
                                }

                                @Override
                                public void onSetFailure(String s) {

                                }
                            }, new MediaConstraints());
                        }else{
                            peerConnection.createAnswer(sdpObserver, new MediaConstraints());
                        }
                        Log.d("createanswer" , "오퍼받고 앤서생성직후");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on(ANSWER, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = (JSONObject) args[0];
                        String type = obj.optString("type");
                        Log.d("answer class" ,"앤서 배치성공");

                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type),
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
                        String tf = String.valueOf(platform.equals(web));
                        Log.d("plat", tf);
                        if(platform.equals(web) == true){
                            Log.d("plat","web" );
                            peerConnection.addIceCandidate(new IceCandidate(obj.getString(SDP_MID),
                                    obj.getInt(SDP_M_LINE_INDEX),
                                    obj.getString("candidate")));
                        }else{
                            Log.d("plat","app" );
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
            try {
                JSONObject obj = new JSONObject();
                String type = sessionDescription.type.toString().toLowerCase();
                obj.put("type", type);
                obj.put(SDP, sessionDescription.description);
                Log.d("type sdp" , sessionDescription.type.toString());
                //obj.put(ROOMNAME , roomname);
                if (createOffer) {
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

    PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d("RTCAPP", "onSignalingChange:" + signalingState.toString());
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d("RTCAPP", "onIceConnectionChange:" + iceConnectionState.toString());
        }


        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            try {
                Log.d("Candidate" , iceCandidate.toString());
                JSONObject obj = new JSONObject();
                obj.put(SDP_MID, iceCandidate.sdpMid);
                obj.put(SDP_M_LINE_INDEX, iceCandidate.sdpMLineIndex);
                obj.put(SDP, iceCandidate.sdp);
                socket.emit(CANDIDATE, roomname,obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            mediaStream.videoTracks.getFirst().addRenderer(otherPeerRenderer);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            mediaStream.videoTracks.getLast().removeRenderer(otherPeerRenderer);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }
    };
}