package com.example.auto.platform;

import com.example.auto.dto.ExcelUploadResult;

import java.util.List;
import java.util.Map;

/**
 * 플랫폼별 배치 업로드 서비스 인터페이스
 * 각 플랫폼(네이버, 쿠팡, 11번가)은 이 인터페이스를 구현합니다.
 */
public interface BatchUploadService {
    
    /**
     * 엑셀 파일에서 읽은 상품들을 배치로 업로드
     * 
     * @param storeId 스토어 ID
     * @param excelRows 엑셀 행 데이터 리스트
     * @return 업로드 결과 리포트
     */
    default ExcelUploadResult batchUploadProducts(Long storeId, List<Map<String, Object>> excelRows) {
        // 하위 호환성을 위해 기본값으로 TestCompany 사용
        return batchUploadProducts(storeId, excelRows, "TestCompany");
    }
    
    /**
     * 엑셀 파일에서 읽은 상품들을 배치로 업로드 (회사명 지정)
     * 
     * @param storeId 스토어 ID
     * @param excelRows 엑셀 행 데이터 리스트
     * @param company 회사명 (회사별 엑셀 컬럼 구조 구분용)
     * @return 업로드 결과 리포트
     */
    ExcelUploadResult batchUploadProducts(Long storeId, List<Map<String, Object>> excelRows, String company);
}

