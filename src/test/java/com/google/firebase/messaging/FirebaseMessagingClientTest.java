/*
 * Copyright  2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.FirebaseRequestInitializer;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class FirebaseMessagingClientTest {

  private static final String TEST_FCM_URL =
      "https://fcm.googleapis.com/v1/projects/test-project/messages:send";

  private static final List<Integer> HTTP_ERRORS = ImmutableList.of(401, 400, 404, 500);

  private static final String MOCK_RESPONSE = "{\"name\": \"mock-name\"}";

  private static final String MOCK_BATCH_SUCCESS_RESPONSE = TestUtils.loadResource(
      "fcm_batch_success.txt");

  private static final String MOCK_BATCH_FAILURE_RESPONSE = TestUtils.loadResource(
      "fcm_batch_failure.txt");

  @Test
  public void testFromApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    try {
      FirebaseMessagingClient client = FirebaseMessagingClient.fromApp(app);
      assertEquals(
          "https://fcm.googleapis.com/v1/projects/test-project/messages:send",
          client.getFcmSendUrl());
      assertTrue(client.getRequestFactory().getInitializer() instanceof FirebaseRequestInitializer);
      assertNull(client.getChildRequestFactory().getInitializer());
      assertSame(client.getJsonFactory(), options.getJsonFactory());
    } finally {
      app.delete();
    }
  }

  @Test
  public void testSend() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = messagingClientWithResponse(response, interceptor);
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
      response.setContent(MOCK_RESPONSE);

      String resp = client.send(entry.getKey(), false);

      assertEquals("mock-name", resp);
      checkRequestHeader(interceptor.getLastRequest());
      checkRequest(interceptor.getLastRequest(),
          ImmutableMap.<String, Object>of("message", entry.getValue()));
    }
  }

  @Test
  public void testSendDryRun() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = messagingClientWithResponse(response, interceptor);
    Map<Message, Map<String, Object>> testMessages = buildTestMessages();

    for (Map.Entry<Message, Map<String, Object>> entry : testMessages.entrySet()) {
      response.setContent(MOCK_RESPONSE);

      String resp = client.send(entry.getKey(), true);

      assertEquals("mock-name", resp);
      checkRequestHeader(interceptor.getLastRequest());
      checkRequest(interceptor.getLastRequest(),
          ImmutableMap.of("message", entry.getValue(), "validate_only", true));
    }
  }

  @Test
  public void testSendError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = messagingClientWithResponse(response, interceptor);

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");
      try {
        client.send(Message.builder().setTopic("test-topic").build(), false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("unknown-error", e.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: {}",
            e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithZeroContentResponse() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = messagingClientWithResponse(response, interceptor);

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setZeroContent();
      try {
        client.send(Message.builder().setTopic("test-topic").build(), false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("unknown-error", e.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: null",
            e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithDetails() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = messagingClientWithResponse(response, interceptor);

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");
      try {
        client.send(Message.builder().setTopic("test-topic").build(), false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("invalid-argument", e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithCanonicalCode() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = messagingClientWithResponse(response, interceptor);

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"NOT_FOUND\", \"message\": \"test error\"}}");
      try {
        client.send(Message.builder().setTopic("test-topic").build(), false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("registration-token-not-registered", e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendErrorWithFcmError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = messagingClientWithResponse(response, interceptor);

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}");
      try {
        client.send(Message.builder().setTopic("test-topic").build(), false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("registration-token-not-registered", e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAll() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = clientWithBatchResponse(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);
    List<Message> messages = ImmutableList.of(
        Message.builder().setTopic("topic1").build(),
        Message.builder().setTopic("topic2").build()
    );

    BatchResponse responses = client.sendAll(messages, false);

    assertSendBatchSuccess(responses, interceptor);
  }

  @Test
  public void testSendAllFailure() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient client = clientWithBatchResponse(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    List<Message> messages = ImmutableList.of(
        Message.builder().setTopic("topic1").build(),
        Message.builder().setTopic("topic2").build(),
        Message.builder().setTopic("topic3").build()
    );

    BatchResponse responses = client.sendAll(messages, false);

    assertSendBatchFailure(responses, interceptor);
  }

  @Test
  public void testSendAllHttpError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient messaging = messagingClientWithResponse(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent("{}");

      try {
        messaging.sendAll(messages, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("unknown-error", e.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: {}",
            e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllTransportError() {
    FirebaseMessagingClient messaging = initFaultyTransportMessaging();
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());

    try {
      messaging.sendAll(messages, false);
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException e) {
      assertEquals("internal-error", e.getErrorCode());
      assertEquals("Error while calling FCM backend service", e.getMessage());
      assertTrue(e.getCause() instanceof IOException);
    }
  }

  @Test
  public void testSendAllErrorWithEmptyResponse() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient messaging = messagingClientWithResponse(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setZeroContent();

      try {
        messaging.sendAll(messages, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("unknown-error", e.getErrorCode());
        assertEquals("Unexpected HTTP response with status: " + code + "; body: null",
            e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithDetails() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient messaging = messagingClientWithResponse(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\"}}");

      try {
        messaging.sendAll(messages, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("invalid-argument", e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithCanonicalCode() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient messaging = messagingClientWithResponse(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"NOT_FOUND\", \"message\": \"test error\"}}");

      try {
        messaging.sendAll(messages, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("registration-token-not-registered", e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithFcmError() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient messaging = messagingClientWithResponse(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", \"message\": \"test error\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}");

      try {
        messaging.sendAll(messages, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("registration-token-not-registered", e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  @Test
  public void testSendAllErrorWithoutMessage() {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessagingClient messaging = messagingClientWithResponse(response, interceptor);
    List<Message> messages = ImmutableList.of(Message.builder()
        .setTopic("test-topic")
        .build());

    for (int code : HTTP_ERRORS) {
      response.setStatusCode(code).setContent(
          "{\"error\": {\"status\": \"INVALID_ARGUMENT\", "
              + "\"details\":[{\"@type\": \"type.googleapis.com/google.firebase.fcm"
              + ".v1.FcmError\", \"errorCode\": \"UNREGISTERED\"}]}}");

      try {
        messaging.sendAll(messages, false);
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals("registration-token-not-registered", e.getErrorCode());
        assertTrue(e.getMessage().startsWith("Unexpected HTTP response"));
        assertTrue(e.getCause() instanceof HttpResponseException);
      }
      checkBatchRequestHeader(interceptor.getLastRequest());
    }
  }

  private static FirebaseMessagingClient initFaultyTransportMessaging() {
    FailingHttpTransport transport = new FailingHttpTransport();
    return FirebaseMessagingClient.builder()
        .setProjectId("test-project")
        .setRequestFactory(transport.createRequestFactory())
        .setChildRequestFactory(transport.createRequestFactory())
        .setJsonFactory(Utils.getDefaultJsonFactory())
        .build();
  }

  private static class FailingHttpTransport extends HttpTransport {
    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      throw new IOException("transport error");
    }
  }

  private void assertSendBatchSuccess(
      BatchResponse batchResponse, TestResponseInterceptor interceptor) throws IOException {

    assertEquals(2, batchResponse.getSuccessCount());
    assertEquals(0, batchResponse.getFailureCount());

    List<SendResponse> responses = batchResponse.getResponses();
    assertEquals(2, responses.size());
    for (int i = 0; i < 2; i++) {
      SendResponse sendResponse = responses.get(i);
      assertTrue(sendResponse.isSuccessful());
      assertEquals("projects/test-project/messages/" + (i + 1), sendResponse.getMessageId());
      assertNull(sendResponse.getException());
    }
    checkBatchRequestHeader(interceptor.getLastRequest());
    checkBatchRequest(interceptor.getLastRequest(), 2);
  }

  private void assertSendBatchFailure(
      BatchResponse batchResponse, TestResponseInterceptor interceptor) throws IOException {

    assertEquals(1, batchResponse.getSuccessCount());
    assertEquals(2, batchResponse.getFailureCount());

    List<SendResponse> responses = batchResponse.getResponses();
    assertEquals(3, responses.size());
    SendResponse firstResponse = responses.get(0);
    assertTrue(firstResponse.isSuccessful());
    assertEquals("projects/test-project/messages/1", firstResponse.getMessageId());
    assertNull(firstResponse.getException());

    SendResponse secondResponse = responses.get(1);
    assertFalse(secondResponse.isSuccessful());
    assertNull(secondResponse.getMessageId());
    FirebaseMessagingException exception = secondResponse.getException();
    assertNotNull(exception);
    assertEquals("invalid-argument", exception.getErrorCode());

    SendResponse thirdResponse = responses.get(2);
    assertFalse(thirdResponse.isSuccessful());
    assertNull(thirdResponse.getMessageId());
    exception = thirdResponse.getException();
    assertNotNull(exception);
    assertEquals("invalid-argument", exception.getErrorCode());

    checkBatchRequestHeader(interceptor.getLastRequest());
    checkBatchRequest(interceptor.getLastRequest(), 3);
  }

  private void checkBatchRequestHeader(HttpRequest request) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals("https://fcm.googleapis.com/batch", request.getUrl().toString());
  }

  private void checkBatchRequest(HttpRequest request, int expectedParts) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    String[] lines = out.toString().split("\n");
    assertEquals(expectedParts, countLinesWithPrefix(lines, "POST " + TEST_FCM_URL));
    assertEquals(expectedParts, countLinesWithPrefix(lines, "x-goog-api-format-version: 2"));
  }

  private int countLinesWithPrefix(String[] lines, String prefix) {
    int matchCount = 0;
    for (String line : lines) {
      if (line.trim().startsWith(prefix)) {
        matchCount++;
      }
    }
    return matchCount;
  }

  private void checkRequest(
      HttpRequest request, Map<String, Object> expected) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    JsonParser parser = Utils.getDefaultJsonFactory().createJsonParser(out.toString());
    Map<String, Object> parsed = new HashMap<>();
    parser.parseAndClose(parsed);
    assertEquals(expected, parsed);
  }

  private void checkRequestHeader(HttpRequest request) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals(TEST_FCM_URL, request.getUrl().toString());
    assertEquals("2", request.getHeaders().get("X-GOOG-API-FORMAT-VERSION"));
  }

  private static FirebaseMessagingClient clientWithBatchResponse(
      String responsePayload, TestResponseInterceptor interceptor) {
    MockLowLevelHttpResponse httpResponse = new MockLowLevelHttpResponse()
        .setContentType("multipart/mixed; boundary=test_boundary")
        .setContent(responsePayload);
    return messagingClientWithResponse(httpResponse, interceptor);
  }

  private static Map<Message, Map<String, Object>> buildTestMessages() {
    ImmutableMap.Builder<Message, Map<String, Object>> builder = ImmutableMap.builder();

    // Empty message
    builder.put(
        Message.builder().setTopic("test-topic").build(),
        ImmutableMap.<String, Object>of("topic", "test-topic"));

    // Notification message
    builder.put(
        Message.builder()
            .setNotification(new Notification("test title", "test body"))
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "notification", ImmutableMap.of("title", "test title", "body", "test body")));

    // Data message
    builder.put(
        Message.builder()
            .putData("k1", "v1")
            .putData("k2", "v2")
            .putAllData(ImmutableMap.of("k3", "v3", "k4", "v4"))
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4")));

    // Android message
    builder.put(
        Message.builder()
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(TimeUnit.SECONDS.toMillis(123))
                .setRestrictedPackageName("test-package")
                .setCollapseKey("test-key")
                .setNotification(AndroidNotification.builder()
                    .setClickAction("test-action")
                    .setTitle("test-title")
                    .setBody("test-body")
                    .setIcon("test-icon")
                    .setColor("#112233")
                    .setTag("test-tag")
                    .setSound("test-sound")
                    .setTitleLocalizationKey("test-title-key")
                    .setBodyLocalizationKey("test-body-key")
                    .addTitleLocalizationArg("t-arg1")
                    .addAllTitleLocalizationArgs(ImmutableList.of("t-arg2", "t-arg3"))
                    .addBodyLocalizationArg("b-arg1")
                    .addAllBodyLocalizationArgs(ImmutableList.of("b-arg2", "b-arg3"))
                    .setChannelId("channel-id")
                    .build())
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "android", ImmutableMap.of(
                "priority", "high",
                "collapse_key", "test-key",
                "ttl", "123s",
                "restricted_package_name", "test-package",
                "notification", ImmutableMap.builder()
                    .put("click_action", "test-action")
                    .put("title", "test-title")
                    .put("body", "test-body")
                    .put("icon", "test-icon")
                    .put("color", "#112233")
                    .put("tag", "test-tag")
                    .put("sound", "test-sound")
                    .put("title_loc_key", "test-title-key")
                    .put("title_loc_args", ImmutableList.of("t-arg1", "t-arg2", "t-arg3"))
                    .put("body_loc_key", "test-body-key")
                    .put("body_loc_args", ImmutableList.of("b-arg1", "b-arg2", "b-arg3"))
                    .put("channel_id", "channel-id")
                    .build()
            )
        ));

    // APNS message
    builder.put(
        Message.builder()
            .setApnsConfig(ApnsConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putAllCustomData(ImmutableMap.<String, Object>of("k1", "v1", "k2", true))
                .setAps(Aps.builder()
                    .setBadge(42)
                    .setAlert(ApsAlert.builder()
                        .setTitle("test-title")
                        .setSubtitle("test-subtitle")
                        .setBody("test-body")
                        .build())
                    .build())
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "apns", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "payload", ImmutableMap.of("k1", "v1", "k2", true,
                    "aps", ImmutableMap.<String, Object>of("badge", new BigDecimal(42),
                        "alert", ImmutableMap.<String, Object>of(
                            "title", "test-title", "subtitle", "test-subtitle",
                            "body", "test-body"))))
        ));

    // Webpush message (no notification)
    builder.put(
        Message.builder()
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putData("k1", "v1")
                .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "webpush", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"))
        ));

    // Webpush message (simple notification)
    builder.put(
        Message.builder()
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putData("k1", "v1")
                .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
                .setNotification(new WebpushNotification("test-title", "test-body", "test-icon"))
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "webpush", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
                "notification", ImmutableMap.of(
                    "title", "test-title", "body", "test-body", "icon", "test-icon"))
        ));

    // Webpush message (all fields)
    builder.put(
        Message.builder()
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader("h1", "v1")
                .putAllHeaders(ImmutableMap.of("h2", "v2", "h3", "v3"))
                .putData("k1", "v1")
                .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
                .setNotification(WebpushNotification.builder()
                    .setTitle("test-title")
                    .setBody("test-body")
                    .setIcon("test-icon")
                    .setBadge("test-badge")
                    .setImage("test-image")
                    .setLanguage("test-lang")
                    .setTag("test-tag")
                    .setData(ImmutableList.of("arbitrary", "data"))
                    .setDirection(WebpushNotification.Direction.AUTO)
                    .setRenotify(true)
                    .setRequireInteraction(false)
                    .setSilent(true)
                    .setTimestampMillis(100L)
                    .setVibrate(new int[]{200, 100, 200})
                    .addAction(new WebpushNotification.Action("action1", "title1"))
                    .addAllActions(
                        ImmutableList.of(
                            new WebpushNotification.Action("action2", "title2", "icon2")))
                    .putCustomData("k4", "v4")
                    .putAllCustomData(ImmutableMap.<String, Object>of("k5", "v5", "k6", "v6"))
                    .build())
                .build())
            .setTopic("test-topic")
            .build(),
        ImmutableMap.<String, Object>of(
            "topic", "test-topic",
            "webpush", ImmutableMap.of(
                "headers", ImmutableMap.of("h1", "v1", "h2", "v2", "h3", "v3"),
                "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
                "notification", ImmutableMap.builder()
                    .put("title", "test-title")
                    .put("body", "test-body")
                    .put("icon", "test-icon")
                    .put("badge", "test-badge")
                    .put("image", "test-image")
                    .put("lang", "test-lang")
                    .put("tag", "test-tag")
                    .put("data", ImmutableList.of("arbitrary", "data"))
                    .put("renotify", true)
                    .put("requireInteraction", false)
                    .put("silent", true)
                    .put("dir", "auto")
                    .put("timestamp", new BigDecimal(100))
                    .put("vibrate", ImmutableList.of(
                        new BigDecimal(200), new BigDecimal(100), new BigDecimal(200)))
                    .put("actions", ImmutableList.of(
                        ImmutableMap.of("action", "action1", "title", "title1"),
                        ImmutableMap.of("action", "action2", "title", "title2", "icon", "icon2")))
                    .put("k4", "v4")
                    .put("k5", "v5")
                    .put("k6", "v6")
                    .build())
        ));

    return builder.build();
  }

  private static FirebaseMessagingClient messagingClientWithResponse(
      MockLowLevelHttpResponse mockResponse, HttpResponseInterceptor interceptor) {
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();
    return FirebaseMessagingClient.builder()
        .setProjectId("test-project")
        .setRequestFactory(transport.createRequestFactory())
        .setChildRequestFactory(transport.createRequestFactory())
        .setJsonFactory(Utils.getDefaultJsonFactory())
        .setResponseInterceptor(interceptor)
        .build();
  }
}
