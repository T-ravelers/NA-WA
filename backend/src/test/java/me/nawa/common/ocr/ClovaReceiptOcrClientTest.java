package me.nawa.common.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClovaReceiptOcrClientTest {

    private static final String INVOKE_URL =
        "https://ocr.example.com/custom/v1/1/2/document/receipt";
    private static final String SECRET_KEY = "secret-key";
    private static final byte[] IMAGE = "fake-image".getBytes(StandardCharsets.UTF_8);

    private static final String SUCCESS_RESPONSE = """
        {
          "version": "V2",
          "requestId": "req-1",
          "images": [{
            "inferResult": "SUCCESS",
            "receipt": {
              "result": {
                "subResults": [{
                  "items": [
                    {
                      "name": {"text": "아메리카노", "formatted": {"value": "아메리카노"}},
                      "count": {"text": "2", "formatted": {"value": "2"}},
                      "price": {
                        "price": {"text": "9,000", "formatted": {"value": "9000"}},
                        "unitPrice": {"text": "4,500", "formatted": {"value": "4500"}}
                      }
                    },
                    {
                      "name": {"text": "치즈케이크"},
                      "price": {"price": {"text": "7,500원"}}
                    }
                  ]
                }],
                "totalPrice": {"price": {"text": "16,500", "formatted": {"value": "16500"}}}
              }
            }
          }]
        }
        """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void recognize_receiptResponse_readsItemsAndTotal() {
        server.expect(request -> {
            assertEquals(INVOKE_URL, request.getURI().toString());
            assertEquals(HttpMethod.POST, request.getMethod());
            assertEquals(SECRET_KEY, request.getHeaders().getFirst("X-OCR-SECRET"));
            JsonNode body = objectMapper.readTree(
                ((MockClientHttpRequest) request).getBodyAsString()
            );
            assertEquals("V2", body.path("version").asText());
            assertEquals("png", body.path("images").path(0).path("format").asText());
            assertEquals(
                Base64.getEncoder().encodeToString(IMAGE),
                body.path("images").path(0).path("data").asText()
            );
        }).andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        RecognizedReceipt receipt = client(INVOKE_URL, SECRET_KEY).recognize(IMAGE, "png");

        List<RecognizedReceiptItem> items = receipt.items();
        assertEquals(2, items.size());
        assertEquals("아메리카노", items.get(0).name());
        assertEquals(new BigDecimal("2"), items.get(0).quantity());
        assertEquals(new BigDecimal("4500"), items.get(0).unitPrice());
        assertEquals(new BigDecimal("9000"), items.get(0).lineTotal());
        assertEquals(new BigDecimal("16500"), receipt.totalPrice());
        server.verify();
    }

    /** 다듬은 값이 없으면 사진에서 읽은 글자를 쓰고, 쉼표나 "원" 같은 군더더기는 떼어낸다. */
    @Test
    void recognize_rawTextOnly_stripsCurrencyDecoration() {
        server.expect(anyRequest())
            .andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

        RecognizedReceiptItem item =
            client(INVOKE_URL, SECRET_KEY).recognize(IMAGE, "png").items().get(1);

        assertEquals("치즈케이크", item.name());
        assertEquals(new BigDecimal("7500"), item.lineTotal());
        assertNull(item.quantity());
        assertNull(item.unitPrice());
    }

    @Test
    void recognize_inferResultNotSuccess_throwsUnreadable() {
        server.expect(anyRequest()).andRespond(withSuccess(
            "{\"images\":[{\"inferResult\":\"FAILURE\"}]}", MediaType.APPLICATION_JSON
        ));

        ReceiptOcrException exception = assertThrows(
            ReceiptOcrException.class,
            () -> client(INVOKE_URL, SECRET_KEY).recognize(IMAGE, "png")
        );

        assertEquals(ReceiptOcrException.Reason.UNREADABLE, exception.getReason());
    }

    @Test
    void recognize_serverError_throwsUnavailable() {
        server.expect(anyRequest()).andRespond(withServerError());

        ReceiptOcrException exception = assertThrows(
            ReceiptOcrException.class,
            () -> client(INVOKE_URL, SECRET_KEY).recognize(IMAGE, "png")
        );

        assertEquals(ReceiptOcrException.Reason.UNAVAILABLE, exception.getReason());
    }

    /** 기다리다 끊긴 것은 그대로 다시 시도할 만하므로 다른 사유로 갈라져야 한다. */
    @Test
    void recognize_readTimeout_throwsTimeout() {
        server.expect(anyRequest()).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        ReceiptOcrException exception = assertThrows(
            ReceiptOcrException.class,
            () -> client(INVOKE_URL, SECRET_KEY).recognize(IMAGE, "png")
        );

        assertEquals(ReceiptOcrException.Reason.TIMEOUT, exception.getReason());
    }

    /** 접속 정보가 없으면 서버를 부르지 않고 바로 사용 불가로 답한다. */
    @Test
    void recognize_withoutConfiguration_throwsUnavailableWithoutCall() {
        ReceiptOcrException exception = assertThrows(
            ReceiptOcrException.class, () -> client("", "").recognize(IMAGE, "png")
        );

        assertEquals(ReceiptOcrException.Reason.UNAVAILABLE, exception.getReason());
        server.verify();
    }

    /** 이 시험이 확인하려는 것은 요청 내용이 아니라 응답 처리라 요청은 따지지 않는다. */
    private static RequestMatcher anyRequest() {
        return request -> {
        };
    }

    private ClovaReceiptOcrClient client(String invokeUrl, String secretKey) {
        return new ClovaReceiptOcrClient(
            new ClovaOcrProperties(invokeUrl, secretKey, 3000, 10000), restTemplate
        );
    }
}
