/*
 * Copyright 2013 Worldpay
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.worldpay.sdk.integration;

import com.worldpay.api.client.common.enums.OrderStatus;
import com.worldpay.gateway.clearwater.client.core.dto.CountryCode;
import com.worldpay.gateway.clearwater.client.core.dto.CurrencyCode;
import com.worldpay.gateway.clearwater.client.core.dto.common.Address;
import com.worldpay.gateway.clearwater.client.core.dto.common.Entry;
import com.worldpay.gateway.clearwater.client.core.dto.common.MerchantUrlConfig;
import com.worldpay.gateway.clearwater.client.core.dto.request.*;
import com.worldpay.gateway.clearwater.client.core.dto.response.CardResponse;
import com.worldpay.gateway.clearwater.client.core.dto.response.OrderResponse;
import com.worldpay.gateway.clearwater.client.core.dto.response.TokenResponse;
import com.worldpay.gateway.clearwater.client.core.exception.WorldpayException;
import com.worldpay.sdk.OrderService;
import com.worldpay.sdk.WorldpayRestClient;
import com.worldpay.sdk.util.HttpUrlConnection;
import com.worldpay.sdk.util.JsonParser;
import com.worldpay.sdk.util.PropertyUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class OrderServiceIT {

    /**
     * Test Master card number.
     */
    private static final String TEST_MASTERCARD_NUMBER = "5555 5555 5555 4444";

    /**
     * Card Verification code.
     */
    private static final String TEST_CVC = "123";

    /**
     * Apm Name
     */
    private static final String APM_NAME = "paypal";

    /**
     * Success url
     */
    public static final String SUCCESS_URL = "http://www.wp.com/success";

    /**
     * Cancel url
     */
    public static final String CANCEL_URL = "http://www.wp.com/cancel";

    /**
     * Failure url
     */
    public static final String FAILURE_URL = "http://www.wp.com/failure";

    /**
     * Pending url
     */
    public static final String PENDING_URL = "http://www.wp.com/pending";

    /**
     * Service under test
     */
    private OrderService orderService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        orderService = new WorldpayRestClient(PropertyUtils.serviceKey()).getOrderService();
    }

    /**
     * This test for creating an order with valid token
     */
    @Test
    public void shouldCreateOrderForValidToken() {

        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);

        assertThat("Response code", response.getOrderCode(), is(notNullValue()));
        assertThat("Amount", response.getAmount(), is(1999));
        assertThat("Customer identifier", response.getKeyValueResponse().getCustomerIdentifiers(), is(notNullValue()));
        assertThat("Card Type", ((CardResponse) response.getPaymentResponse()).getCardType(),
                   equalTo("MASTERCARD_CREDIT"));
    }

    /**
     * Test for creating 3DS order with valid token and 3DS information.
     */
    @Test
    public void shouldCreateOrderForValidTokenAndThreeDS() {

        OrderRequest orderRequest = createOrderRequestWithThreeDS();
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Response code", response.getOrderCode(), is(notNullValue()));
        assertThat("Amount", response.getAmount(), is(1999));
        assertThat("Customer identifier", response.getKeyValueResponse().getCustomerIdentifiers(), is(notNullValue()));
    }

    /**
     * This is the test for creating the 3D Order.
     * This test expects authorize3Ds to return {@link OrderResponse} and order status should be Success.
     */
    @Test
    public void shouldAuthorizeThreeDSOrder() {
        OrderRequest orderRequest = createOrderRequestWithThreeDS();
        orderRequest.setName("3D");
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Order code", response.getOrderCode(), notNullValue());
        assertThat("Order Status", response.getPaymentStatus(), equalTo(OrderStatus.PRE_AUTHORIZED.toString()));

        OrderAuthorizationRequest orderAuthorizationRequest =
            createOrderAuthorizationRequest(orderRequest.getThreeDSecureInfo(), "IDENTIFIED");
        OrderResponse authorizeRespone = orderService.authorize3Ds(response.getOrderCode(), orderAuthorizationRequest);
        assertThat("Response", authorizeRespone, notNullValue());
        assertThat("Order code", authorizeRespone.getOrderCode(), equalTo(response.getOrderCode()));
        assertThat("Order Status", authorizeRespone.getPaymentStatus(), equalTo(OrderStatus.SUCCESS.toString()));
    }

    /**
     * This is the test for testing 3DS order with invalid 3DS relevant information.
     */
    @Test(expected = WorldpayException.class)
    public void shouldThrowExceptionIfThreeDSEnabledButInfoInvalid() {

        OrderRequest orderRequest = createOrderRequestWithThreeDS();
        orderRequest.setThreeDSecureInfo(null);
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Response code", response.getOrderCode(), is(notNullValue()));
        assertThat("Amount", response.getAmount(), is(1999));
        assertThat("Customer identifier", response.getKeyValueResponse().getCustomerIdentifiers(), is(notNullValue()));
    }

    /**
     * This is the test for testing Alternate Payment Methods such as PayPal.
     */
    @Test
    public  void shouldCreateOrderForValidTokenAndAlternatePaymentMethod(){

        OrderRequest orderRequest = createOrderRequestWithAPM();
        orderRequest.setToken(createApmToken());

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Response code", response.getOrderCode(), is(notNullValue()));
        assertThat("Amount", response.getAmount(), is(1999));
        assertThat("Customer identifier", response.getKeyValueResponse().getCustomerIdentifiers(), is(notNullValue()));
        assertThat("Redirect URL", response.getRedirectURL(), is(notNullValue()));
    }

    /**
     * This is the test for full refund an order
     */
    @Test
    public void shouldRefundOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());

        String orderCode = orderService.create(orderRequest).getOrderCode();
        assertThat("Order code", orderCode, is(notNullValue()));

        orderService.refund(orderCode);
    }

    /**
     * This is the test for partial refund an order
     */
    @Test
    public void shouldPartialRefundOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());

        String orderCode = orderService.create(orderRequest).getOrderCode();
        assertThat("Order code", orderCode, is(notNullValue()));

        orderService.refund(orderCode, 1);
    }

    /**
     * This is the test for creating an order with invalid token.
     * Expects API error
     */
    @Test
    public void shouldThrowExceptionForInvalidToken() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken("invalid-token");
        try {
            orderService.create(orderRequest);
        } catch (WorldpayException e) {
            assertThat("Valid token", e.getApiError().getCustomCode(), is("TKN_NOT_FOUND"));
        }
    }


    /**
     * Create an order request with three DS enabled
     *
     * @return {@link OrderRequest}
     */
    private OrderRequest createOrderRequestWithThreeDS() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setIs3DSOrder(true);

        ThreeDSecureInfo threeDSecureInfo = createThreeDsSecureInfo();
        orderRequest.setThreeDSecureInfo(threeDSecureInfo);

        return orderRequest;
    }

    /**
     * Create an order request with an APM
     *
     * @return {@link OrderRequest}
     */
    private OrderRequest createOrderRequestWithAPM() {
        OrderRequest orderRequest = createOrderRequest();

        MerchantUrlConfig merchantUrlConfig = new MerchantUrlConfig();
        merchantUrlConfig.setSuccessUrl(SUCCESS_URL);
        merchantUrlConfig.setCancelUrl(CANCEL_URL);
        merchantUrlConfig.setFailureUrl(FAILURE_URL);
        merchantUrlConfig.setPendingUrl(PENDING_URL);
        orderRequest.setMerchantUrlConfig(merchantUrlConfig);

        return orderRequest;
    }

    /**
     * Create a test ThreeDSecureInfo
     *
     * @return the test ThreeDSecureInfo
     */
    private ThreeDSecureInfo createThreeDsSecureInfo() {
        ThreeDSecureInfo threeDSecureInfo = new ThreeDSecureInfo();
        threeDSecureInfo.setShopperAcceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        threeDSecureInfo.setShopperIpAddress("195.35.90.111");
        threeDSecureInfo.setShopperSessionId("021ui8ib1");
        threeDSecureInfo.setShopperUserAgent(
            "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-GB; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)");
        return threeDSecureInfo;
    }

    /**
     * Create a test OrderAuthorizationRequest
     *
     * @return return the test OrderAuthorizationRequest
     */
    private OrderAuthorizationRequest createOrderAuthorizationRequest(ThreeDSecureInfo threeDSecureInfo,
                                                                      String threeDsResponseCode) {
        OrderAuthorizationRequest orderAuthorizationRequest = new OrderAuthorizationRequest();
        orderAuthorizationRequest.setThreeDSResponseCode(threeDsResponseCode);
        orderAuthorizationRequest.setThreeDSecureInfo(threeDSecureInfo);
        return orderAuthorizationRequest;
    }

    /**
     * Create a test OrderRequest
     *
     * @return {@link OrderRequest}
     */
    private OrderRequest createOrderRequest() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setAmount(1999);
        orderRequest.setCurrencyCode(CurrencyCode.GBP);
        orderRequest.setName("test name");
        orderRequest.setOrderDescription("test description");
        orderRequest.setBillingAddress(createAddress());

        List<Entry> customerIdentifiers = new ArrayList<Entry>();
        Entry entry = new Entry("test key 1", "test value 1");
        customerIdentifiers.add(entry);

        orderRequest.setCustomerIdentifiers(customerIdentifiers);
        return orderRequest;
    }

    private Address createAddress(){
        Address address = new Address();
        address.setAddress1("line 1");
        address.setAddress2("line 2");
        address.setCity("city");
        address.setCountryCode(CountryCode.GB);
        address.setPostalCode("AB1 2CD");

        return address;
    }

    /**
     * Create a token
     *
     * @return token
     */
    private String createToken() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientKey(PropertyUtils.getProperty("clientKey"));

        CardRequest cardRequest = new CardRequest();
        cardRequest.setCardNumber(TEST_MASTERCARD_NUMBER);
        cardRequest.setCvc(TEST_CVC);
        cardRequest.setName("javalib client");
        cardRequest.setExpiryMonth(2);
        cardRequest.setExpiryYear(2018);

        tokenRequest.setPaymentMethod(cardRequest);

        return getToken(tokenRequest);
    }

    /**
     * Create a token for an alternate payment method.
     *
     * @return Alternate Payment Method Token
     */
    private String createApmToken() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientKey(PropertyUtils.getProperty("clientKey"));

        AlternatePaymentMethod alternatePaymentMethod = new AlternatePaymentMethod();
        alternatePaymentMethod.setApmName(APM_NAME);
        alternatePaymentMethod.setShopperCountryCode(CountryCode.GB);

        Map<String, String> apmFields = new HashMap<String, String>();
        apmFields.put("bankId", "some value");
        apmFields.put("someOtherId", "some value");

        alternatePaymentMethod.setApmFields(apmFields);

        tokenRequest.setPaymentMethod(alternatePaymentMethod);
        return getToken(tokenRequest);
    }

    /**
     * Post request to fetch token.
     *
     * @param tokenRequest the request to get a token
     *
     * @return token value
     */
    private String getToken(TokenRequest tokenRequest) {
        final String json = JsonParser.toJson(tokenRequest);

        String fullUri = PropertyUtils.getProperty("tokenUrl");
        HttpURLConnection httpURLConnection = HttpUrlConnection.getConnection(fullUri);
        try {
            httpURLConnection.setRequestMethod("POST");
            DataOutputStream dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());
            dataOutputStream.writeBytes(json);

            TokenResponse tokenResponse = JsonParser.toObject(httpURLConnection.getInputStream(), TokenResponse.class);

            return tokenResponse.getToken();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
