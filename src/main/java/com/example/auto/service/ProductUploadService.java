package com.example.auto.service;

import com.example.auto.constants.PlatformConstants;
import com.example.auto.dto.ExcelUploadResult;
import com.example.auto.domain.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 업로드 서비스
 * 엑셀 파일 업로드 및 플랫폼별 배치 업로드를 처리합니다.
 * 
 * 배치 업로드는 트랜잭션을 사용하지 않습니다. 각 항목별로 독립적으로 처리되며,
 * 일부 항목이 실패해도 다른 항목의 처리는 계속됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductUploadService {
    
    private final ExcelService excelService;
    private final StoreService storeService;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // 플랫폼별 배치 업로드 서비스 캐시 (플랫폼명 -> 서비스 인스턴스)
    private final Map<String, Object> platformBatchServices = new HashMap<>();
    
    /**
     * 엑셀 파일을 업로드하여 플랫폼별로 상품을 등록합니다.
     * 
     * @param file 엑셀 파일
     * @param platform 플랫폼 코드 (naver, coupang, 11st)
     * @param company 회사명 (회사별 엑셀 컬럼 구조 구분용, 기본값: TestCompany)
     * @return 업로드 결과
     * @throws IllegalArgumentException 파일 검증 실패 또는 플랫폼 오류
     * @throws IOException 파일 읽기 실패
     */
    public ExcelUploadResult uploadProductsFromExcel(MultipartFile file, String platform, String company) 
            throws IllegalArgumentException, IOException {
        
        // 회사명 기본값 설정
        if (company == null || company.trim().isEmpty()) {
            company = "TestCompany";
        }
        company = company.trim();
        
        log.info("상품 업로드 서비스 시작: fileName={}, size={}, platform={}, company={}", 
                file.getOriginalFilename(), file.getSize(), platform, company);
        
        // 플랫폼 검증
        validatePlatform(platform);
        
        // 파일 검증
        validateFile(file);
        
        // 플랫폼 정규화
        String platformLower = PlatformConstants.normalize(platform);
        
        // 스토어 조회 (쿠팡은 스토어가 필요 없음)
        Long storeId = null;
        if (!PlatformConstants.COUPANG.equals(platformLower)) {
            // 네이버 등 다른 플랫폼은 스토어가 필요함
            Store currentStore = storeService.getCurrentStore()
                    .orElseThrow(() -> new IllegalArgumentException("등록된 스토어가 없습니다. 먼저 스토어를 등록해주세요."));
            storeId = currentStore.getId();
        }
        
        // 엑셀 파일 파싱
        List<Map<String, Object>> excelRows = parseExcelFile(file);
        
        // 플랫폼별 배치 업로드 실행 (회사명 전달)
        ExcelUploadResult result = executeBatchUpload(platformLower, storeId, excelRows, company);
        
        log.info("상품 업로드 서비스 완료: 플랫폼={}, 회사={}, 총 {}개, 성공 {}개, 실패 {}개", 
                platformLower, company, result.getTotalCount(), result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * 플랫폼 검증
     */
    private void validatePlatform(String platform) {
        if (platform == null || platform.trim().isEmpty()) {
            throw new IllegalArgumentException("플랫폼을 선택해주세요. " + PlatformConstants.PLATFORMS_ERROR_MESSAGE);
        }
        
        String platformLower = PlatformConstants.normalize(platform);
        if (!PlatformConstants.isSupported(platformLower)) {
            throw new IllegalArgumentException("지원하지 않는 플랫폼입니다. " + PlatformConstants.PLATFORMS_ERROR_MESSAGE + " 중 선택");
        }
    }
    
    /**
     * 파일 검증
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("엑셀 파일이 비어있습니다.");
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            throw new IllegalArgumentException("엑셀 파일 형식이 올바르지 않습니다. (.xlsx 또는 .xls 파일만 지원)");
        }
    }
    
    /**
     * 엑셀 파일 파싱
     */
    private List<Map<String, Object>> parseExcelFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        log.info("엑셀 파일 파싱 시작: {}", fileName);
        
        List<Map<String, Object>> excelRows = excelService.parseExcelFile(file);
        
        if (excelRows == null || excelRows.isEmpty()) {
            throw new IllegalArgumentException("엑셀 파일에 데이터가 없습니다.");
        }
        
        log.info("엑셀 파일 파싱 완료: {}개 행", excelRows.size());
        return excelRows;
    }
    
    /**
     * 플랫폼별 배치 업로드 실행
     * SUPPORTED_PLATFORMS에 추가된 플랫폼을 자동으로 인식합니다.
     * 
     * @param platform 플랫폼 코드
     * @param storeId 스토어 ID
     * @param excelRows 엑셀 행 데이터
     * @param company 회사명 (회사별 엑셀 컬럼 구조 구분용)
     * @return 업로드 결과
     */
    private ExcelUploadResult executeBatchUpload(String platform, Long storeId, List<Map<String, Object>> excelRows, String company) {
        log.info("플랫폼별 배치 업로드 실행: 플랫폼={}, 스토어 ID={}, 행 수={}, 회사={}", platform, storeId, excelRows.size(), company);
        
        // 플랫폼별 BatchUploadService 동적 찾기
        // 네이밍 규칙: {platform}BatchUploadService (예: naverBatchUploadService, coupangBatchUploadService)
        String serviceBeanName = getPlatformServiceBeanName(platform);
        
        Object platformService;
        try {
            platformService = applicationContext.getBean(serviceBeanName);
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
            log.warn("{} 플랫폼의 배치 업로드 서비스를 찾을 수 없습니다: {}", platform, serviceBeanName);
            throw new IllegalArgumentException(
                    String.format("%s 플랫폼은 아직 지원되지 않습니다. %s 서비스를 구현해주세요.", 
                            platform, serviceBeanName));
        }
        
        try {
            // 리플렉션을 사용하여 batchUploadProducts 메서드 호출 (회사명 파라미터 추가)
            java.lang.reflect.Method method = platformService.getClass()
                    .getMethod("batchUploadProducts", Long.class, List.class, String.class);
            
            log.info("{} 플랫폼으로 배치 업로드 시작 (회사: {})", platform, company);
            return (ExcelUploadResult) method.invoke(platformService, storeId, excelRows, company);
            
        } catch (NoSuchMethodException e) {
            // 회사명 파라미터가 없는 기존 메서드 시그니처 시도 (하위 호환성)
            try {
                java.lang.reflect.Method method = platformService.getClass()
                        .getMethod("batchUploadProducts", Long.class, List.class);
                
                log.info("{} 플랫폼으로 배치 업로드 시작 (기존 메서드 사용, 회사명 무시)", platform);
                return (ExcelUploadResult) method.invoke(platformService, storeId, excelRows);
            } catch (Exception e2) {
                log.error("{} 플랫폼 배치 업로드 실행 중 오류 발생", platform, e2);
                throw new IllegalArgumentException(
                        String.format("%s 플랫폼 배치 업로드 실행 중 오류가 발생했습니다: %s", 
                                platform, e2.getMessage()));
            }
        } catch (Exception e) {
            log.error("{} 플랫폼 배치 업로드 실행 중 오류 발생", platform, e);
            throw new IllegalArgumentException(
                    String.format("%s 플랫폼 배치 업로드 실행 중 오류가 발생했습니다: %s", 
                            platform, e.getMessage()));
        }
    }
    
    /**
     * 플랫폼명으로 서비스 빈 이름 생성
     * 예: "naver" -> "naverBatchUploadService"
     *     "coupang" -> "coupangBatchUploadService"
     *     "11st" -> "elevenStreetBatchUploadService"
     */
    private String getPlatformServiceBeanName(String platform) {
        // 플랫폼명을 소문자로 정규화
        String normalized = platform.toLowerCase();
        
        // 특수 케이스 처리 (11st -> elevenStreet)
        if ("11st".equalsIgnoreCase(platform) || PlatformConstants.ELEVEN_STREET.equalsIgnoreCase(platform)) {
            normalized = "elevenStreet";
        }
        
        // 빈 이름 생성: {platform}BatchUploadService
        return normalized + "BatchUploadService";
    }
}

