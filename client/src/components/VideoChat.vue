<script setup>
import {onMounted, onUnmounted, ref} from "vue";

const localVideo = ref();
const remoteVideo = ref();
const localStream = ref();
const peerConnection = ref();
const webSocket = ref();

// const myKey = Math.random().toString(36).substring(2, 11);
// const pcListMap = ref({});
// const roomId = ref("");
// const otherKeyList = ref([]);

const startWebSocket = async () => {
  webSocket.value = new WebSocket('ws://localhost:8080/ws/signaling');

  webSocket.value.onmessage = (event) => {
    try {
      const {type, data} = JSON.parse(event.data);
      console.log('Message Type:', type);
      if (type === 'offerResponse') {
        handleOffer(data);
      } else if (type === 'answerResponse') {
        handleAnswer(data);
      } else if (type === 'iceCandidateResponse') {
        handleCandidate(data);
      }
    } catch (error) {
      console.log('onmessage', event)
    }
  };
}

const startCall = async () => {
  localStream.value = await navigator.mediaDevices.getUserMedia({video: true, audio: true});
  localVideo.value.srcObject = localStream.value;

  const configuration = {iceServers: [{urls: 'stun:stun.l.google.com:19302'}]};
  peerConnection.value = new RTCPeerConnection(configuration);

  // 로컬 스트림을 Peer Connection에 추가
  localStream.value.getTracks().forEach(track => {
    peerConnection.value.addTrack(track, localStream.value);
  });

  // ICE 후보 수집 완료 후 진행
  peerConnection.value.onicecandidate = event => {
    if (event.candidate) {
      console.log('ICECandidate:', event.candidate);
      webSocket.value.send(JSON.stringify({ type: 'iceCandidate', data: event.candidate }));
    }
  }

  // 원격 스트림 수신 시 실행
  peerConnection.value.ontrack = event => {
    console.log('OnTrack:', event);
    const [remoteStream] = event.streams;
    remoteVideo.value.srcObject = remoteStream;
  }

  peerConnection.value.oniceconnectionstatechange = event => {
    console.log('OnIceConnectionStateChanged:', event);
  };

  // SDP offer 생성 및 설정
  const offer = await peerConnection.value.createOffer();
  await peerConnection.value.setLocalDescription(offer);
  console.log('Offer:', offer);
  webSocket.value.send(JSON.stringify({ type: 'offer', data: offer }));
}

const handleOffer = async (offer) => {
  await peerConnection.value.setRemoteDescription(new RTCSessionDescription({type:'offer',sdp:offer.sdp}));
  const answer = await peerConnection.value.createAnswer();
  await peerConnection.value.setLocalDescription(answer);
  console.log('Answer:', answer)
  webSocket.value.send(JSON.stringify({ type: 'answer', data: answer }));
}

const handleAnswer = async (answer) => {
  console.log("Current PeerConnection state:", peerConnection.value.signalingState);
  try {
    if (peerConnection.value.signalingState !== "stable") {
      console.log("Attempting to set remote description with answer in non-stable state");
      await peerConnection.value.setRemoteDescription(new RTCSessionDescription({type:'answer', sdp:answer.sdp}));
    } else {
      console.log("Received answer in stable state, not setting remote description");
    }
  } catch (error) {
    console.error("Failed to set remote answer sdp:", error);
  }
}

const handleCandidate = async (candidate) => {
  await peerConnection.value.addIceCandidate(new RTCIceCandidate({
    candidate: candidate.candidate,
    sdpMLineIndex: candidate.sdpMLineIndex,
    sdpMid: candidate.sdpMid,
    usernameFragment: candidate.usernameFragment,
  }));
}

onUnmounted(() => {
  if(peerConnection.value) {
    peerConnection.value.close();
  }
  if(localStream.value) {
    localStream.value.getTracks().forEach(track => {
      track.stop();
    });
  }
  if(webSocket.value && webSocket.value.connect) {
    webSocket.value.close();
  }
})
onMounted(() => {
  startWebSocket();
});
</script>

<template>
  <div>
    <video ref="localVideo" autoplay muted></video>
    <video ref="remoteVideo" autoplay></video>
    <button @click="startCall">Start Call</button>
  </div>
</template>

<style scoped>
video {
  width: 100%;
  max-width: 450px;
  border: 1px solid black;
}
</style>