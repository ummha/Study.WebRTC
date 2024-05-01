<template>
  <div class="about">
    <h1>This is an chat page</h1>

    <VideoChat></VideoChat>
  </div>
</template>

<style>
@media (min-width: 1024px) {
  .about {
    min-height: 100vh;
    display: flex;
    align-items: center;
  }
}
</style>
<script setup>
import VideoChat from "@/components/VideoChat.vue";

</script>