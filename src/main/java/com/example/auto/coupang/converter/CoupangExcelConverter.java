package com.example.auto.coupang.converter;

import java.util.Map;

/**
 * 쿠팡 엑셀 데이터를 쿠팡 API 형식으로 변환하는 인터페이스
 * 회사별로 다른 엑셀 컬럼 구조를 처리하기 위한 전략 패턴 인터페이스
 */
public interface CoupangExcelConverter {
    
    /**
     * 엑셀 행 데이터를 쿠팡 API 상품 등록 형식으로 변환
     * 
     * @param rowData 엑셀 행 데이터 (Map)
     * @param vendorId 업체코드
     * @return 쿠팡 API 상품 등록 데이터 (Map)
     * @throws IllegalArgumentException 필수 필드가 없거나 형식이 잘못된 경우
     */
    Map<String, Object> convert(Map<String, Object> rowData, String vendorId);
}
