package me.nawa.common.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;

/**
 * 네이버 CLOVA OCR의 영수증 인식을 부른다.
 *
 * 사진을 글자로 바꾸는 일만 하고, 그 값이 정산에 쓸 만한지는 판단하지 않는다. 읽어낸 값을
 * 다듬는 일은 정산 쪽 몫이다.
 */
@Slf4j
@Component
public class ClovaReceiptOcrClient implements ReceiptOcrClient {

    private static final String SECRET_HEADER = "X-OCR-SECRET";
    private static final String REQUEST_VERSION = "V2";
    private static final String IMAGE_NAME = "receipt";
    private static final String INFER_SUCCESS = "SUCCESS";

    /** 금액 글자에는 쉼표, 통화 기호, "원" 같은 군더더기가 섞여 들어온다. */
    private static final String NON_NUMERIC = "[^0-9.-]";

    private final ClovaOcrProperties properties;
    private final RestOperations restOperations;

    public ClovaReceiptOcrClient(
        ClovaOcrProperties properties,
        @Qualifier("clovaOcrRestOperations") RestOperations restOperations
    ) {
        this.properties = properties;
        this.restOperations = restOperations;
    }

    @Override
    public RecognizedReceipt recognize(byte[] image, String imageFormat) {
        if (!properties.isConfigured()) {
            throw new ReceiptOcrException(
                ReceiptOcrException.Reason.UNAVAILABLE, "CLOVA OCR 접속 정보가 설정되지 않았습니다."
            );
        }
        return parse(call(image, imageFormat));
    }

    private JsonNode call(byte[] image, String imageFormat) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(SECRET_HEADER, properties.getSecretKey());

        OcrRequest body = new OcrRequest(
            REQUEST_VERSION,
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            List.of(new OcrImage(imageFormat, IMAGE_NAME, encode(image)))
        );

        try {
            ResponseEntity<JsonNode> response = restOperations.exchange(
                properties.getInvokeUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class
            );
            JsonNode payload = response.getBody();
            if (payload == null) {
                throw new ReceiptOcrException(
                    ReceiptOcrException.Reason.UNAVAILABLE, "CLOVA OCR 응답이 비어 있습니다."
                );
            }
            return payload;
        } catch (ResourceAccessException exception) {
            // 상대 서버에 닿지 못한 경우다. 기다리다 끊긴 것인지 아예 연결이 안 된 것인지는
            // 원인 예외로만 갈린다. 둘을 뭉뚱그리면 화면이 "다시 시도해 보세요"와 "잠시 후
            // 이용해 주세요"를 구분해 안내할 수 없다.
            boolean timedOut = hasCause(exception, SocketTimeoutException.class);
            log.warn("CLOVA OCR 호출 실패, timeout={}", timedOut);
            throw new ReceiptOcrException(
                timedOut ? ReceiptOcrException.Reason.TIMEOUT
                    : ReceiptOcrException.Reason.UNAVAILABLE,
                "CLOVA OCR 서버에 닿지 못했습니다.", exception
            );
        } catch (RestClientResponseException exception) {
            // 응답 본문에는 우리가 보낸 사진에서 읽은 내용이 담길 수 있으므로 상태 코드만
            // 남긴다.
            log.warn("CLOVA OCR 오류 응답, status={}", exception.getRawStatusCode());
            throw new ReceiptOcrException(
                ReceiptOcrException.Reason.UNAVAILABLE, "CLOVA OCR이 오류를 응답했습니다.", exception
            );
        } catch (RestClientException exception) {
            log.warn("CLOVA OCR 응답을 해석하지 못했습니다.");
            throw new ReceiptOcrException(
                ReceiptOcrException.Reason.UNAVAILABLE, "CLOVA OCR 응답을 해석하지 못했습니다.",
                exception
            );
        }
    }

    private RecognizedReceipt parse(JsonNode payload) {
        // 결과가 통째로 없으면 사진 탓이 아니라 응답이 우리가 아는 모양이 아니라는 뜻이다.
        // 이때 "다시 찍어 보세요"라고 안내하면 사용자는 고쳐지지 않는 일을 계속 반복한다.
        JsonNode image = payload.path("images").path(0);
        if (image.isMissingNode()) {
            log.warn("CLOVA OCR 응답에 인식 결과가 없습니다.");
            throw new ReceiptOcrException(
                ReceiptOcrException.Reason.UNAVAILABLE, "CLOVA OCR 응답에 인식 결과가 없습니다."
            );
        }
        if (!INFER_SUCCESS.equals(image.path("inferResult").asText())) {
            throw new ReceiptOcrException(
                ReceiptOcrException.Reason.UNREADABLE, "사진에서 영수증을 읽어내지 못했습니다."
            );
        }

        // 읽기는 성공했는데 영수증 모양의 결과가 없는 경우다. 호출 주소가 영수증 도메인이
        // 아닐 때 이렇게 온다. 사진 문제로 안내하면 주소가 잘못됐다는 사실이 끝까지 드러나지
        // 않고, 사용자는 멀쩡한 영수증을 계속 다시 찍게 된다.
        JsonNode result = image.path("receipt").path("result");
        if (result.isMissingNode()) {
            log.warn("CLOVA OCR 응답에 영수증 결과가 없습니다. 호출 주소를 확인해야 합니다.");
            throw new ReceiptOcrException(
                ReceiptOcrException.Reason.UNAVAILABLE, "CLOVA OCR 응답이 영수증 결과가 아닙니다."
            );
        }

        List<RecognizedReceiptItem> items = new ArrayList<>();
        // 한 장에 매장이 나뉘어 찍히면 묶음(subResults)이 여러 개로 나온다. 사용자가 보기에는
        // 어차피 영수증 한 장이므로 묶음 구분 없이 한 줄로 이어 붙인다.
        for (JsonNode subResult : result.path("subResults")) {
            for (JsonNode item : subResult.path("items")) {
                items.add(toItem(item));
            }
        }

        return new RecognizedReceipt(
            List.copyOf(items), number(result.path("totalPrice").path("price"))
        );
    }

    private static RecognizedReceiptItem toItem(JsonNode item) {
        JsonNode price = item.path("price");
        return new RecognizedReceiptItem(
            text(item.path("name")),
            number(item.path("count")),
            number(price.path("unitPrice")),
            number(price.path("price"))
        );
    }

    /**
     * 인식된 글자는 사진에서 읽은 그대로({@code text})와 다듬은 값
     * ({@code formatted.value}) 두 벌로 온다. 다듬은 값이 있으면 쉼표나 단위가 정리된
     * 값이라 그쪽을 먼저 쓴다.
     */
    private static String text(JsonNode field) {
        String formatted = field.path("formatted").path("value").asText("").trim();
        if (!formatted.isEmpty()) {
            return formatted;
        }
        String raw = field.path("text").asText("").trim();
        return raw.isEmpty() ? null : raw;
    }

    /** 숫자로 읽히지 않으면 비워 둔다. 비어 있는 값은 사용자가 화면에서 채운다. */
    private static BigDecimal number(JsonNode field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll(NON_NUMERIC, "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String encode(byte[] image) {
        return Base64.getEncoder().encodeToString(image);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable cause = throwable;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return true;
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    private record OcrRequest(
        String version,
        String requestId,
        long timestamp,
        List<OcrImage> images
    ) {
    }

    private record OcrImage(String format, String name, String data) {
    }
}
