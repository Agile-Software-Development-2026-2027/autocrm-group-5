package it.devmethodologies.meetingingestor.controller;


import it.devmethodologies.meetingingestor.service.impl.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class FirefliesWebhookController {

    private final MeetingService meetingService;

    @GetMapping("/fireflies-test")
    public ResponseEntity<String> test(@RequestParam String meetingId) {
        meetingService.processMeetingTranscript(meetingId);          // parte in background (@Async)
        return ResponseEntity.accepted()                             // 202: "ricevuto, ci sto lavorando"
                .body("Elaborazione avviata per il meeting " + meetingId);
    }


    @PostMapping("/fireflies")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature", required = false) String signature) {


        // validate sign

        //map obj(wrap in try catch to handle exception)

        // call service async to get the meeting transcript from fireflies api

        return ResponseEntity.ok().build();

    }
}
