const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function sampleRecording(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    title: "Conversation with Contractor",
    notes: "Discussed kitchen renovation estimate",
    category: "Personal",
    tags: ["renovation", "budget"],
    durationMs: 42500,
    recordedAtMs: Date.now() - 5000,
    language: "English",
    transcriptStatus: "COMPLETED",
    fullTranscript: "The budget is around 45000 rupees for cabinets.",
    summaryTopics: "Kitchen cabinet pricing and timeline",
    summaryDecisions: "Compare two wood finishes",
    summaryTasks: "Confirm measurements on Monday",
    summaryLocations: "Anna Nagar showroom",
    summaryFinancials: "45000 INR",
    summaryDates: "Monday 10 AM",
    localAudioFileName: "rec_001.m4a",
    waveformPeaks: [12, 34, 67, 45, 22],
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("recordings").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_1").set(sampleRecording(ALICE_UID))
  );
});

test("Owner can create, update, list, and delete recordings with exact Kotlin payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const recRef = aliceDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_1");

  await assertSucceeds(recRef.set(sampleRecording(ALICE_UID)));

  // Partial update of mutable fields only
  await assertSucceeds(
    recRef.update({
      title: "Updated Kitchen Budget",
      notes: "Confirmed wood finish",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List subcollection succeeds even when querying all user recordings
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("recordings").get());

  // Reject ghost field / @DocumentId id field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_2").set({
      ...sampleRecording(ALICE_UID),
      id: "rec_2",
    })
  );

  // Reject createdAt mutation
  await assertFails(
    recRef.update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(recRef.delete());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's memories", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_1").set(sampleRecording(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("recordings").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_1").delete());
});

test("Segments, extractedInfo, reminders, and memoryMessages enforce schema and ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Segment
  const segRef = aliceDb
    .collection("users").doc(ALICE_UID)
    .collection("recordings").doc("rec_1")
    .collection("segments").doc("seg_1");
  await assertSucceeds(
    segRef.set({
      userId: ALICE_UID,
      recordingId: "rec_1",
      startMs: 1200,
      endMs: 4800,
      text: "Take the Chennai-Bangalore highway.",
      speaker: "Friend",
      confidence: 0.94,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(segRef.update({ text: "Take the Chennai-Bengaluru highway.", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("recordings").doc("rec_1").collection("segments").get());

  // ExtractedInfo
  const infoRef = aliceDb.collection("users").doc(ALICE_UID).collection("extractedInfo").doc("info_1");
  await assertSucceeds(
    infoRef.set({
      userId: ALICE_UID,
      recordingId: "rec_1",
      recordingTitle: "Road Trip",
      segmentId: "seg_1",
      timestampMs: 1200,
      category: "DIRECTION",
      label: "Highway Route",
      value: "Chennai-Bangalore highway",
      verified: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(infoRef.update({ verified: true, updatedAt: new Date(Date.now() - 500) }));

  // Reminder
  const remRef = aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1");
  await assertSucceeds(
    remRef.set({
      userId: ALICE_UID,
      recordingId: "rec_1",
      recordingTitle: "Road Trip",
      timestampMs: 1200,
      title: "Call contractor before noon",
      dueDateTimeText: "Tomorrow 10:00 AM",
      dueTimestampMs: Date.now() + 86400000,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(remRef.update({ completed: true, updatedAt: new Date(Date.now() - 500) }));

  // MemoryMessage (append-only, updates disallowed)
  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("memoryMessages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      role: "ASSISTANT",
      text: "Your friend mentioned taking the Chennai-Bangalore highway.",
      citedRecordingId: "rec_1",
      citedRecordingTitle: "Road Trip",
      citedDateText: "Nov 23, 2026",
      citedTimestampMs: 1200,
      citedExcerpt: "Take the Chennai-Bangalore highway.",
      evidenceConfidence: "HIGH",
      createdAt: now,
    })
  );
  await assertFails(msgRef.update({ text: "Tampered answer" }));
});
