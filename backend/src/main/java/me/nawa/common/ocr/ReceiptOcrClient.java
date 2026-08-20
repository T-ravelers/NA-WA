package me.nawa.common.ocr;

/** 영수증 사진에서 품목 글자를 읽어내는 바깥 서비스와의 계약이다. */
public interface ReceiptOcrClient {

    /**
     * 사진을 인식 서버에 보내고 읽어낸 결과를 돌려준다.
     *
     * @param image       사진의 원본 바이트
     * @param imageFormat 인식 서버가 알아듣는 형식 이름(예: {@code jpg}, {@code png})
     * @throws ReceiptOcrException 인식하지 못했거나 서버를 부르지 못한 경우
     */
    RecognizedReceipt recognize(byte[] image, String imageFormat);
}
