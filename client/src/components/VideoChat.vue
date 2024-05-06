<script setup>
import {onMounted, onUnmounted, ref} from "vue";
import SockJS from "sockjs-client";
import {Stomp} from "stompjs/lib/stomp.js";


const localVideo = ref();
const remoteVideo = ref();
const localStream = ref();
const peerConnection = ref();
const socket = ref();

const myKey = Math.random().toString(36).substring(2, 11);
const pcListMap = ref(new Map());
const roomId = ref("1234");
const otherKeyList = ref([]);
const stompClient = ref();

const startWebSocket = async () => {
  socket.value = new SockJS("http://localhost:8080/signaling");
  stompClient.value = Stomp.over(socket.value);

  stompClient.value.connect({}, () => {
    stompClient.value.subscribe(`/topic/peer/iceCandidate/${myKey}/${roomId}`, candidate => {
      const key = JSON.parse(candidate.body).key
      const message = JSON.parse(candidate.body).body;

      // 해당 key에 해당되는 peer 에 받은 정보를 addIceCandidate 해준다.
      pcListMap.value.get(key).addIceCandidate(new RTCIceCandidate({candidate:message.candidate,sdpMLineIndex:message.sdpMLineIndex,sdpMid:message.sdpMid}));
    });

    stompClient.value.subscribe(`/topic/peer/offer/${myKey}/${roomId}`, offer => {
      const key = JSON.parse(offer.body).key;
      const message = JSON.parse(offer.body).body;

      // 해당 key에 새로운 peerConnection 를 생성해준후 pcListMap 에 저장해준다.
      pcListMap.value.set(key, createPeerConnection(key));
      // 생성한 peer 에 offer정보를 setRemoteDescription 해준다.
      pcListMap.value.get(key).setRemoteDescription(new RTCSessionDescription({type:message.type,sdp:message.sdp}));
      //sendAnswer 함수를 호출해준다.
      sendAnswer(pcListMap.value.get(key), key);

    });

    //answer peer 교환을 위한 subscribe
    stompClient.value.subscribe(`/topic/peer/answer/${myKey}/${roomId}`, answer =>{
      const key = JSON.parse(answer.body).key;
      const message = JSON.parse(answer.body).body;

      // 해당 key에 해당되는 Peer 에 받은 정보를 setRemoteDescription 해준다.
      pcListMap.value.get(key).setRemoteDescription(new RTCSessionDescription(message));
    });

    //key를 보내라는 신호를 받은 subscribe
    stompClient.value.subscribe(`/topic/call/key`, message =>{
      //자신의 key를 보내는 send
      stompClient.value.send(`/app/send/key`, {}, JSON.stringify(myKey));

    });

    //상대방의 key를 받는 subscribe
    stompClient.value.subscribe(`/topic/send/key`, message => {
      const key = JSON.parse(message.body);

      //만약 중복되는 키가 ohterKeyList에 있는지 확인하고 없다면 추가해준다.
      if(myKey !== key && otherKeyList.value.find((mapKey) => mapKey === myKey) === undefined){
        otherKeyList.value.push(key);
      }
    });
  });
}

const startCam = async () => {
  // localStream.value = await navigator.mediaDevices.getUserMedia({video: true, audio: true});
  localStream.value = await navigator.mediaDevices.getDisplayMedia({video: true, audio: false});
  console.log("localStream", localStream.value);
  localVideo.value.srcObject = localStream.value;
}

const createPeerConnection = (otherKey) =>{
  const configuration = {iceServers: [{urls: 'stun:stun.l.google.com:19302'}]};
  const pc = new RTCPeerConnection(configuration);
  try {
    // peerConnection 에서 icecandidate 이벤트가 발생시 onIceCandidate 함수 실행
    pc.onicecandidate = (event) => {
      onIceCandidate(event, otherKey);
    };
    // peerConnection 에서 track 이벤트가 발생시 onTrack 함수를 실행
    pc.ontrack = event => {
      onTrack(event, otherKey);
    }
    // 만약 localStream 이 존재하면 peerConnection에 addTrack 으로 추가함
    if(localStream.value !== undefined){
      localStream.value.getTracks().forEach(track => {
        pc.addTrack(track, localStream.value);
      });
    }
    console.log('PeerConnection created');
  } catch (error) {
    console.error('PeerConnection failed: ', error);
  }
  return pc;
}

//onIceCandidate
const onIceCandidate = (event, otherKey) => {
  if (event.candidate) {
    console.log('ICE candidate');
    stompClient.value.send(`/app/peer/iceCandidate/${otherKey}/${roomId}`,{}, JSON.stringify({
      key : myKey,
      body : event.candidate
    }));
  }
};

//onTrack
const onTrack = (event, otherKey) => {
  const [remoteStream] = event.streams;
  remoteVideo.value.srcObject = remoteStream;
  // if(document.getElementById(`${otherKey}`) === null) {
  //   const video =  document.createElement('video');
  //
  //   video.autoplay = true;
  //   video.controls = true;
  //   video.id = otherKey;
  //   video.srcObject = event.streams[0];
  //
  //   document.getElementById('remoteStreamDiv').appendChild(video);
  // }
};

const sendOffer = (pc ,otherKey) => {
  pc.createOffer().then(offer => {
    setLocalAndSendMessage(pc, offer);
    stompClient.value.send(`/app/peer/offer/${otherKey}/${roomId}`, {}, JSON.stringify({
      key : myKey,
      body : offer
    }));
    console.log('Send offer');
  });
};

const sendAnswer = (pc,otherKey) => {
  pc.createAnswer().then( answer => {
    setLocalAndSendMessage(pc ,answer);
    stompClient.value.send(`/app/peer/answer/${otherKey}/${roomId}`, {}, JSON.stringify({
      key : myKey,
      body : answer
    }));
    console.log('Send answer');
  });
};

const setLocalAndSendMessage = (pc, sessionDescription) =>{
  pc.setLocalDescription(sessionDescription);
}

const startStream = async () => {
  await stompClient.value.send(`/app/call/key`, {}, {});

  setTimeout(() =>{
    otherKeyList.value.map((key) =>{
      if(!pcListMap.value.has(key)){
        pcListMap.value.set(key, createPeerConnection(key));
        sendOffer(pcListMap.value.get(key),key);
      }
    });
    console.log(1000)
  },1000);
  // otherKeyList.value.map((key) =>{
  //   if(!pcListMap.value.has(key)){
  //     pcListMap.value.set(key, createPeerConnection(key));
  //     sendOffer(pcListMap.value.get(key),key);
  //   }
  // });
}

onUnmounted(() => {
  if(pcListMap.value.length > 0) {
    pcListMap.value.forEach(pc => pc.close());
    pcListMap.value.clear();
  }
  if(peerConnection.value) {
    peerConnection.value.close();
  }
  if(localStream.value) {
    localStream.value.getTracks().forEach(track => {
      track.stop();
    });
  }
  if(stompClient.value && stompClient.value.connected) {
    stompClient.value.close();
  }
  if(socket.value.connected) {
    socket.value.close();
  }
})
onMounted(async () => {
  try {
    await startCam();
    await startWebSocket();
  } catch (error) {
    console.error(error);
  }
});
</script>

<template>
  <div>KEY: {{ myKey }}, room: {{ roomId }}</div>
  <div>{{ pcListMap }}</div>
  <div>
    <video ref="localVideo" autoplay muted></video>
    <video ref="remoteVideo" autoplay></video>
    <button class="btn-stream" @click="startStream">Start Stream</button>
  </div>
</template>

<style scoped>
video {
  width: 100%;
  max-width: 400px;
  margin-right: 10px;
  border: 1px solid black;
}
button.btn-stream {
  display: block;
}
</style>