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
import com.google.firebase.testing.TestResponseInterceptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class InstanceIdClientTest {

  private static final String TEST_IID_SUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchAdd";

  private static final String TEST_IID_UNSUBSCRIBE_URL =
      "https://iid.googleapis.com/iid/v1:batchRemove";

  private static final List<Integer> HTTP_ERRORS = ImmutableList.of(401, 404, 500);

  private static InstanceIdClient clientWithResponse(
      MockLowLevelHttpResponse mockResponse, HttpResponseInterceptor interceptor) {
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(mockResponse)
        .build();
    return new InstanceIdClient(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        interceptor);
  }

  @Test
  public void testSubscribe() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = clientWithResponse(response, interceptor);

    response.setContent(responseString);
    TopicManagementResponse result = messaging.subscribeToTopic(
        "test-topic", ImmutableList.of("id1", "id2"));
    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testSubscribeError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = clientWithResponse(response, interceptor);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      try {
        messaging.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals(getTopicManagementErrorCode(statusCode), e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
    }
  }

  @Test
  public void testSubscribeUnknownError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = clientWithResponse(response, interceptor);
    response.setStatusCode(500).setContent("{}");
    try {
      messaging.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException e) {
      assertEquals(getTopicManagementErrorCode(500), e.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500; body: {}", e.getMessage());
      assertTrue(e.getCause() instanceof HttpResponseException);
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_SUBSCRIBE_URL);
  }

  @Test
  public void testSubscribeTransportError() throws Exception {
    InstanceIdClient messaging = initFaultyTransportMessaging();
    try {
      messaging.subscribeToTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException e) {
      assertEquals("internal-error", e.getErrorCode());
      assertEquals("Error while calling IID backend service", e.getMessage());
      assertTrue(e.getCause() instanceof IOException);
    }
  }

  @Test
  public void testUnsubscribe() throws Exception {
    final String responseString = "{\"results\": [{}, {\"error\": \"error_reason\"}]}";
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = clientWithResponse(response, interceptor);

    response.setContent(responseString);
    TopicManagementResponse result = messaging.unsubscribeFromTopic(
        "test-topic", ImmutableList.of("id1", "id2"));
    checkTopicManagementRequestHeader(
        interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    checkTopicManagementRequest(interceptor.getLastRequest(), result);
  }

  @Test
  public void testUnsubscribeError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = clientWithResponse(response, interceptor);
    for (int statusCode : HTTP_ERRORS) {
      response.setStatusCode(statusCode).setContent("{\"error\": \"test error\"}");
      try {
        messaging.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
        fail("No error thrown for HTTP error");
      } catch (FirebaseMessagingException e) {
        assertEquals(getTopicManagementErrorCode(statusCode), e.getErrorCode());
        assertEquals("test error", e.getMessage());
        assertTrue(e.getCause() instanceof HttpResponseException);
      }

      checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
    }
  }

  @Test
  public void testUnsubscribeUnknownError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    InstanceIdClient messaging = clientWithResponse(response, interceptor);
    response.setStatusCode(500).setContent("{}");
    try {
      messaging.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException e) {
      assertEquals(getTopicManagementErrorCode(500), e.getErrorCode());
      assertEquals("Unexpected HTTP response with status: 500; body: {}", e.getMessage());
      assertTrue(e.getCause() instanceof HttpResponseException);
    }

    checkTopicManagementRequestHeader(interceptor.getLastRequest(), TEST_IID_UNSUBSCRIBE_URL);
  }

  @Test
  public void testUnsubscribeTransportError() throws Exception {
    InstanceIdClient messaging = initFaultyTransportMessaging();
    try {
      messaging.unsubscribeFromTopic("test-topic", ImmutableList.of("id1", "id2"));
      fail("No error thrown for HTTP error");
    } catch (FirebaseMessagingException e) {
      assertEquals("internal-error", e.getErrorCode());
      assertEquals("Error while calling IID backend service", e.getMessage());
      assertTrue(e.getCause() instanceof IOException);
    }
  }

  private void checkTopicManagementRequest(
      HttpRequest request, TopicManagementResponse result) throws IOException {
    assertEquals(1, result.getSuccessCount());
    assertEquals(1, result.getFailureCount());
    assertEquals(1, result.getErrors().size());
    assertEquals(1, result.getErrors().get(0).getIndex());
    assertEquals("unknown-error", result.getErrors().get(0).getReason());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    request.getContent().writeTo(out);
    Map<String, Object> parsed = new HashMap<>();
    JsonParser parser = Utils.getDefaultJsonFactory().createJsonParser(out.toString());
    parser.parseAndClose(parsed);
    assertEquals(2, parsed.size());
    assertEquals("/topics/test-topic", parsed.get("to"));
    assertEquals(ImmutableList.of("id1", "id2"), parsed.get("registration_tokens"));
  }

  private void checkTopicManagementRequestHeader(
      HttpRequest request, String expectedUrl) {
    assertEquals("POST", request.getRequestMethod());
    assertEquals(expectedUrl, request.getUrl().toString());
  }

  private static String getTopicManagementErrorCode(int statusCode) {
    String code = InstanceIdClient.IID_ERROR_CODES.get(statusCode);
    if (code == null) {
      code = "unknown-error";
    }
    return code;
  }

  private static InstanceIdClient initFaultyTransportMessaging() {
    FailingHttpTransport transport = new FailingHttpTransport();
    return new InstanceIdClient(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory());
  }

  private static class FailingHttpTransport extends HttpTransport {
    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      throw new IOException("transport error");
    }
  }
}
