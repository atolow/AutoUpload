package com.example.auto.naver.converter;

import com.example.auto.dto.ProductRequest;
import java.util.Map;

/**
 * 네이버 엑셀 데이터를 ProductRequest로 변환하는 인터페이스
 * 회사별로 다른 엑셀 컬럼 구조를 처리하기 위한 전략 패턴 인터페이스
 */
public interface NaverExcelConverter {
    
    /**
     * 엑셀 행 데이터를 ProductRequest로 변환
     * 
     * @param rowData 엑셀 행 데이터 (Map)
     * @return ProductRequest 객체
     * @throws IllegalArgumentException 필수 필드가 없거나 형식이 잘못된 경우
     */
    ProductRequest convert(Map<String, Object> rowData);
}
