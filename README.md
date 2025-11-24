# WebRTC Loopback Android App

**Short description**  
A small Android app that simulates a simplified smart-glasses video streaming system using WebRTC in loopback mode. The app captures video from the rear camera via the Android WebRTC SDK, renders it locally, allows adjusting the requested frame rate (FPS), and logs encoder type, SDP events and ICE events for debugging and evaluation.

---

## Deliverables / What this repo contains
- Complete Android project (Kotlin + Jetpack Compose) that builds on Android Studio.  
- `WebRTCManager.kt` — WebRTC setup, camera capture, loopback PeerConnection, FPS control, and logging.  
- `WebRTCViewModel.kt` — ViewModel wrapper for lifecycle and UI state.  
- `HomeScreen.kt` — Compose UI: SurfaceViewRenderer preview, FPS slider, Start button.  
- This `README.md` with setup steps, the required conceptual answers.

---

## WebRTC Conceptual Questions 

1. What is the role of ICE candidates in WebRTC? Why are they important?

They are  the different ways through which two devices might be able to talk to each other. 
Devices are accessible across different ways like routers, firewalls, different networks, or mobile data, and we rarely know which path will actually work. 
ICE lets WebRTC collect every possible connection route and then test them automatically until it finds a working way. 
So ICE is what makes WebRTC work reliably in the complex world of networks.

2. What is an SDP offer/answer, and when are they exchanged?

It is like two people agreeing on how they want to communicate before they start talking. 
One side proposes the “rules” like which codecs to use, whether to send audio or video, encryption details, etc., and the other side responds with what they can support. 
Only when they agree can media flow. The exchange happens right at the start of the connection before any video or audio is sent. 
In our loopback setup, the phone negotiates with itself. So the offer and then immediately answers it.

3. If your WebRTC stream fails to connect on some networks, what might cause that? How would you investigate it?

Different networks can block or restrict the traffic that WebRTC needs like public Wi-Fi and corporate networks especiall, or both peers are behind strict NATs. 
To investigate, I’d start by looking at the ICE gathering process, which candidates are generated, which are failing, and whether the ICE state ever leaves “checking” 

---

What I’d Improve Next if Given More Time:

I’d enhance the UI with visual indicators for connection quality and ICE states.
I’d improve the UI with clear indicators of connection status, frame rate, and potential errors, making it easier for users to understand what’s happening under the hood.

---


## Quick Setup & Build (step-by-step)

**Clone the repository**

```bash
git clone https://github.com/githussein?tab=repositories
cd webrtcloopback

