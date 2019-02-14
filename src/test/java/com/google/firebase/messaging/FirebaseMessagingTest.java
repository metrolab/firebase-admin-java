/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.util.List;
import org.junit.After;
import org.junit.Test;

public class FirebaseMessagingTest {

  private static final String MOCK_BATCH_SUCCESS_RESPONSE = TestUtils.loadResource(
      "fcm_batch_success.txt");

  private static final String MOCK_BATCH_FAILURE_RESPONSE = TestUtils.loadResource(
      "fcm_batch_failure.txt");

  private static final ImmutableList.Builder<String> TOO_MANY_IDS = ImmutableList.builder();

  static {
    for (int i = 0; i < 1001; i++) {
      TOO_MANY_IDS.add("id" + i);
    }
  }

  private static final List<TopicMgtArgs> INVALID_TOPIC_MGT_ARGS = ImmutableList.of(
      new TopicMgtArgs(null, null),
      new TopicMgtArgs(null, "test-topic"),
      new TopicMgtArgs(ImmutableList.<String>of(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), "test-topic"),
      new TopicMgtArgs(TOO_MANY_IDS.build(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), null),
      new TopicMgtArgs(ImmutableList.of("id"), ""),
      new TopicMgtArgs(ImmutableList.of("id"), "foo*")
  );

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp.initializeApp(options);

    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    assertSame(messaging, FirebaseMessaging.getInstance());
  }

  @Test
  public void testGetInstanceByApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "custom-app");

    FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
    assertSame(messaging, FirebaseMessaging.getInstance(app));
  }

  @Test
  public void testPostDeleteApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "custom-app");
    app.delete();
    try {
      FirebaseMessaging.getInstance(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void testNoProjectId() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp.initializeApp(options);
    try {
      FirebaseMessaging.getInstance();
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testSendNullMessage() {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initDefaultMessaging(interceptor);
    try {
      messaging.sendAsync(null);
      fail("No error thrown for null message");
    } catch (NullPointerException expected) {
      // expected
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testSendMulticastWithNull() {
    FirebaseMessaging messaging = initDefaultMessaging();
    try {
      messaging.sendMulticastAsync(null);
      fail("No error thrown for null multicast message");
    } catch (NullPointerException expected) {
      // expected
    }
  }

  @Test
  public void testSendMulticast() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);
    MulticastMessage multicast = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .build();

    BatchResponse responses = messaging.sendMulticast(multicast);

    //assertSendBatchSuccess(responses, interceptor);
  }

  @Test
  public void testSendMulticastAsync() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_SUCCESS_RESPONSE, interceptor);
    MulticastMessage multicast = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .build();

    BatchResponse responses = messaging.sendMulticastAsync(multicast).get();

    //assertSendBatchSuccess(responses, interceptor);
  }

  @Test
  public void testSendMulticastFailure() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    MulticastMessage multicast = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .addToken("token3")
        .build();

    BatchResponse responses = messaging.sendMulticast(multicast);

    //assertSendBatchFailure(responses, interceptor);
  }

  @Test
  public void testSendMulticastAsyncFailure() throws Exception {
    final TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = getMessagingForBatchRequest(
        MOCK_BATCH_FAILURE_RESPONSE, interceptor);
    MulticastMessage multicast = MulticastMessage.builder()
        .addToken("token1")
        .addToken("token2")
        .addToken("token3")
        .build();

    BatchResponse responses = messaging.sendMulticastAsync(multicast).get();

    //assertSendBatchFailure(responses, interceptor);
  }

  @Test
  public void testSendAllWithNull() {
    FirebaseMessaging messaging = initDefaultMessaging();
    try {
      messaging.sendAllAsync(null);
      fail("No error thrown for null message list");
    } catch (NullPointerException expected) {
      // expected
    }
  }

  @Test
  public void testSendAllWithEmptyList() {
    FirebaseMessaging messaging = initDefaultMessaging();
    try {
      messaging.sendAllAsync(ImmutableList.<Message>of());
      fail("No error thrown for empty message list");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testSendAllWithTooManyMessages() {
    FirebaseMessaging messaging = initDefaultMessaging();
    ImmutableList.Builder<Message> listBuilder = ImmutableList.builder();
    for (int i = 0; i < 1001; i++) {
      listBuilder.add(Message.builder().setTopic("topic").build());
    }
    try {
      messaging.sendAllAsync(listBuilder.build());
      fail("No error thrown for too many messages in the list");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testInvalidSubscribe() {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initDefaultMessaging(interceptor);

    for (TopicMgtArgs args : INVALID_TOPIC_MGT_ARGS) {
      try {
        messaging.subscribeToTopicAsync(args.registrationTokens, args.topic);
        fail("No error thrown for invalid args");
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    assertNull(interceptor.getResponse());
  }

  @Test
  public void testInvalidUnsubscribe() {
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseMessaging messaging = initDefaultMessaging(interceptor);

    for (TopicMgtArgs args : INVALID_TOPIC_MGT_ARGS) {
      try {
        messaging.unsubscribeFromTopicAsync(args.registrationTokens, args.topic);
        fail("No error thrown for invalid args");
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    assertNull(interceptor.getResponse());
  }

  private static FirebaseMessaging initMessaging(
      MockLowLevelHttpResponse mockResponse, HttpResponseInterceptor interceptor) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    return new FirebaseMessaging(app, interceptor);
  }

  private static FirebaseMessaging initDefaultMessaging() {
    return initDefaultMessaging(null);
  }

  private static FirebaseMessaging initDefaultMessaging(HttpResponseInterceptor interceptor) {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    return new FirebaseMessaging(app, interceptor);
  }

  private FirebaseMessaging getMessagingForBatchRequest(
      String responsePayload, TestResponseInterceptor interceptor) {
    MockLowLevelHttpResponse httpResponse = new MockLowLevelHttpResponse()
        .setContentType("multipart/mixed; boundary=test_boundary")
        .setContent(responsePayload);
    return initMessaging(httpResponse, interceptor);
  }

  private static class TopicMgtArgs {
    private final List<String> registrationTokens;
    private final String topic;

    TopicMgtArgs(List<String> registrationTokens, String topic) {
      this.registrationTokens = registrationTokens;
      this.topic = topic;
    }
  }


}
