package com.example.auto.naver.service;

import com.example.auto.dto.ExcelUploadResult;
import com.example.auto.naver.constants.NaverApiConstants;
import com.example.auto.naver.converter.NaverExcelConverter;
import com.example.auto.platform.BatchUploadService;
import com.example.auto.service.CsvExportService;
import com.example.auto.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 네이버 배치 업로드 서비스
 * 엑셀 파일에서 읽은 여러 상품을 순차적으로 네이버 스토어에 업로드
 */
@Slf4j
@Service("naverBatchUploadService")
@RequiredArgsConstructor
public class NaverBatchUploadService implements BatchUploadService {
    
    /**
     * 허용된 회사명 목록
     * 새로운 회사를 추가하려면 이 Set에 회사명을 추가하고, 해당 회사의 Converter 클래스를 생성해야 합니다.
     * 예: ExampleCompany를 추가하려면 Set.of("TestCompany", "ExampleCompany")로 변경
     */
    private static final Set<String> ALLOWED_COMPANIES = Set.of("TestCompany");
    
    private final ExcelService excelService;
    // 기본 Converter (TestCompany)
    private final NaverExcelConverter defaultExcelToProductConverter;
    private final NaverProductService productService;
    private final CsvExportService csvExportService;
    private final BeanFactory beanFactory;
    
    /**
     * 엑셀 파일을 읽어서 상품들을 배치로 업로드 (회사명 지정)
     * 
     * @param storeId 스토어 ID
     * @param excelRows 엑셀 행 데이터 리스트
     * @param company 회사명 (회사별 엑셀 컬럼 구조 구분용)
     * @return 업로드 결과 리포트
     */
    @Override
    public ExcelUploadResult batchUploadProducts(Long storeId, List<Map<String, Object>> excelRows, String company) {
        log.info("배치 업로드 시작: 스토어 ID={}, 총 {}개 행, 회사={}", storeId, excelRows.size(), company);
        
        // 회사명에 따라 적절한 Converter 선택
        NaverExcelConverter excelToProductConverter = getExcelConverter(company);
        
        ExcelUploadResult.ExcelUploadResultBuilder resultBuilder = ExcelUploadResult.builder()
                .totalCount(excelRows.size())
                .successCount(0)
                .failureCount(0);
        
        List<ExcelUploadResult.SuccessItem> successItems = new ArrayList<>();
        List<ExcelUploadResult.FailureItem> failureItems = new ArrayList<>();
        
        // 원본 엑셀 데이터를 Map으로 저장 (CSV 저장용)
        Map<Integer, Map<String, Object>> originalDataMap = new java.util.LinkedHashMap<>();
        
        // 각 행을 순차적으로 처리
        for (Map<String, Object> rowData : excelRows) {
            Integer rowNumber = (Integer) rowData.get("_rowNumber");
            if (rowNumber == null) {
                rowNumber = 0;
            }
            
            // 원본 데이터 저장 (CSV 저장용)
            originalDataMap.put(rowNumber, new java.util.LinkedHashMap<>(rowData));
            
            String productName = null;
            try {
                // 엑셀 행 데이터를 ProductRequest로 변환
                com.example.auto.dto.ProductRequest productRequest = excelToProductConverter.convert(rowData);
                productName = productRequest.getName();
                
                log.info("상품 업로드 시작: 행 {}, 상품명={}", rowNumber, productName);
                
                // Rate Limit 방지를 위해 상품 업로드 사이에 딜레이 추가 (첫 번째 상품 제외)
                if (rowNumber > 1) {
                    try {
                        Thread.sleep(NaverApiConstants.PRODUCT_UPLOAD_DELAY_MS); // Rate Limit 방지
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("상품 업로드 딜레이 중단됨");
                    }
                }
                
                // 네이버 스토어에 상품 등록
                Map<String, Object> response = productService.createProduct(storeId, productRequest);
                
                // 성공 처리
                ExcelUploadResult.SuccessItem successItem = ExcelUploadResult.SuccessItem.builder()
                        .rowNumber(rowNumber)
                        .productName(productName)
                        .response(response)
                        .build();
                successItems.add(successItem);
                
                log.info("상품 업로드 성공: 행 {}, 상품명={}", rowNumber, productName);
                
            } catch (IllegalArgumentException e) {
                // 검증 에러
                log.error("상품 업로드 실패 (검증 에러): 행 {}, 상품명={}, 에러={}", rowNumber, productName, e.getMessage());
                
                ExcelUploadResult.FailureItem failureItem = ExcelUploadResult.FailureItem.builder()
                        .rowNumber(rowNumber)
                        .productName(productName != null ? productName : "파싱 실패")
                        .errorMessage(e.getMessage())
                        .errorType("VALIDATION_ERROR")
                        .build();
                failureItems.add(failureItem);
                
            } catch (Exception e) {
                // 기타 에러 (API 에러 등)
                log.error("상품 업로드 실패: 행 {}, 상품명={}", rowNumber, productName, e);
                
                ExcelUploadResult.FailureItem failureItem = ExcelUploadResult.FailureItem.builder()
                        .rowNumber(rowNumber)
                        .productName(productName != null ? productName : "파싱 실패")
                        .errorMessage(e.getMessage() != null ? e.getMessage() : "알 수 없는 오류")
                        .errorType("API_ERROR")
                        .build();
                failureItems.add(failureItem);
            }
        }
        
        ExcelUploadResult result = resultBuilder
                .successCount(successItems.size())
                .failureCount(failureItems.size())
                .successItems(successItems)
                .failureItems(failureItems)
                .build();
        
        log.info("배치 업로드 완료: 스토어 ID={}, 총 {}개, 성공 {}개, 실패 {}개", 
                storeId, result.getTotalCount(), result.getSuccessCount(), result.getFailureCount());
        
        // CSV 파일 저장
        String successCsvPath = null;
        String failureCsvPath = null;
        try {
            successCsvPath = csvExportService.exportSuccessToCsv(successItems, originalDataMap);
            failureCsvPath = csvExportService.exportFailureToCsv(failureItems, originalDataMap);
            
            if (successCsvPath != null) {
                log.info("성공 CSV 파일 저장됨: {}", successCsvPath);
            }
            if (failureCsvPath != null) {
                log.info("실패 CSV 파일 저장됨: {}", failureCsvPath);
            }
        } catch (Exception e) {
            log.error("CSV 파일 저장 중 오류 발생", e);
        }
        
        // 결과에 CSV 파일 경로 추가
        result.setSuccessCsvPath(successCsvPath);
        result.setFailureCsvPath(failureCsvPath);
        
        return result;
    }
    
    /**
     * 회사명에 따라 적절한 NaverExcelConverter 선택
     * 빈 이름 규칙: {company}NaverExcelConverter (예: testCompanyNaverExcelConverter)
     * 
     * @param company 회사명
     * @return NaverExcelConverter 구현체
     * @throws IllegalArgumentException 허용되지 않은 회사명인 경우
     */
    private NaverExcelConverter getExcelConverter(String company) {
        if (company == null || company.trim().isEmpty()) {
            company = "TestCompany";
        }
        company = company.trim();
        
        // 회사명 검증
        if (!ALLOWED_COMPANIES.contains(company)) {
            throw new IllegalArgumentException(
                String.format("지원하지 않는 회사명입니다: '%s'. 허용된 회사명: %s", 
                    company, ALLOWED_COMPANIES));
        }
        
        // 빈 이름 생성: 첫 글자를 소문자로 변환 (예: TestCompany -> testCompanyNaverExcelConverter)
        String beanName = company.substring(0, 1).toLowerCase() + company.substring(1) + "NaverExcelConverter";
        
        try {
            NaverExcelConverter converter = beanFactory.getBean(beanName, NaverExcelConverter.class);
            log.info("회사별 Converter 선택: 회사={}, 빈 이름={}", company, beanName);
            return converter;
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalArgumentException(
                String.format("회사 '%s'의 Converter를 찾을 수 없습니다 (빈 이름: %s). " +
                    "해당 회사의 Converter 클래스를 생성하고 빈으로 등록해주세요.", 
                    company, beanName), e);
        }
    }
}
