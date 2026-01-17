package com.example.auto.service;

import com.example.auto.dto.ExcelUploadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CSV 파일 내보내기 서비스
 * 상품 업로드 결과를 CSV 파일로 저장
 */
@Slf4j
@Service
public class CsvExportService {
    
    @Value("${app.csv.export.dir:exports}")
    private String exportDir;
    
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * 성공한 상품 데이터를 CSV 파일로 저장
     * 
     * @param successItems 성공한 상품 목록
     * @param originalDataMap 원본 엑셀 데이터 (행 번호 -> 데이터)
     * @return 저장된 파일 경로
     */
    public String exportSuccessToCsv(List<ExcelUploadResult.SuccessItem> successItems, 
                                     Map<Integer, Map<String, Object>> originalDataMap) {
        if (successItems == null || successItems.isEmpty()) {
            log.info("성공한 상품이 없어 CSV 파일을 생성하지 않습니다.");
            return null;
        }
        
        try {
            // 설정된 디렉토리 생성 (절대 경로 또는 상대 경로)
            Path exportDirPath = Paths.get(exportDir).toAbsolutePath();
            if (!Files.exists(exportDirPath)) {
                Files.createDirectories(exportDirPath);
            }
            
            // 파일명 생성: success_yyyyMMdd_HHmmss.csv
            String fileName = "success_" + LocalDateTime.now().format(FILE_DATE_FORMATTER) + ".csv";
            Path filePath = exportDirPath.resolve(fileName);
            
            log.info("CSV 파일 저장 경로: {}", filePath.toAbsolutePath());
            
            // CSV 헤더 추출 (첫 번째 성공 아이템의 원본 데이터에서)
            List<String> headers = extractSuccessHeaders(successItems, originalDataMap);
            
            try (FileWriter writer = new FileWriter(filePath.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                // BOM 추가 (Excel에서 UTF-8 한글 깨짐 방지)
                writer.write('\ufeff');
                
                // 헤더 작성
                writeCsvRow(writer, headers);
                
                // 데이터 행 작성
                for (ExcelUploadResult.SuccessItem item : successItems) {
                    Map<String, Object> originalData = originalDataMap.get(item.getRowNumber());
                    List<String> row = new ArrayList<>();
                    
                    // 원본 엑셀 데이터 추가
                    for (String header : headers) {
                        if ("상품ID".equals(header)) {
                            continue; // 나중에 추가하므로 스킵
                        }
                        Object value = originalData != null ? originalData.get(header) : null;
                        row.add(formatCsvValue(value));
                    }
                    
                    // 상품 ID 추가 (응답에서 추출)
                    String productId = extractProductId(item.getResponse());
                    row.add(productId != null ? productId : "");
                    
                    writeCsvRow(writer, row);
                }
            }
            
            String absolutePath = filePath.toAbsolutePath().toString();
            log.info("성공 CSV 파일 저장 완료: {} ({}개 상품)", absolutePath, successItems.size());
            return absolutePath;
            
        } catch (IOException e) {
            log.error("성공 CSV 파일 저장 실패", e);
            return null;
        }
    }
    
    /**
     * 실패한 상품 데이터를 CSV 파일로 저장
     * 
     * @param failureItems 실패한 상품 목록
     * @param originalDataMap 원본 엑셀 데이터 (행 번호 -> 데이터)
     * @return 저장된 파일 경로
     */
    public String exportFailureToCsv(List<ExcelUploadResult.FailureItem> failureItems,
                                    Map<Integer, Map<String, Object>> originalDataMap) {
        if (failureItems == null || failureItems.isEmpty()) {
            log.info("실패한 상품이 없어 CSV 파일을 생성하지 않습니다.");
            return null;
        }
        
        try {
            // 설정된 디렉토리 생성 (절대 경로 또는 상대 경로)
            Path exportDirPath = Paths.get(exportDir).toAbsolutePath();
            if (!Files.exists(exportDirPath)) {
                Files.createDirectories(exportDirPath);
            }
            
            // 파일명 생성: failure_yyyyMMdd_HHmmss.csv
            String fileName = "failure_" + LocalDateTime.now().format(FILE_DATE_FORMATTER) + ".csv";
            Path filePath = exportDirPath.resolve(fileName);
            
            log.info("CSV 파일 저장 경로: {}", filePath.toAbsolutePath());
            
            // CSV 헤더 추출
            List<String> headers = extractFailureHeaders(failureItems, originalDataMap);
            
            try (FileWriter writer = new FileWriter(filePath.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                // BOM 추가 (Excel에서 UTF-8 한글 깨짐 방지)
                writer.write('\ufeff');
                
                // 헤더 작성 (에러 정보 컬럼 추가)
                List<String> csvHeaders = new ArrayList<>(headers);
                csvHeaders.add("에러타입");
                csvHeaders.add("에러메시지");
                writeCsvRow(writer, csvHeaders);
                
                // 데이터 행 작성
                for (ExcelUploadResult.FailureItem item : failureItems) {
                    Map<String, Object> originalData = originalDataMap.get(item.getRowNumber());
                    List<String> row = new ArrayList<>();
                    
                    // 원본 엑셀 데이터 추가
                    for (String header : headers) {
                        Object value = originalData != null ? originalData.get(header) : null;
                        row.add(formatCsvValue(value));
                    }
                    
                    // 에러 정보 추가
                    row.add(item.getErrorType() != null ? item.getErrorType() : "");
                    row.add(item.getErrorMessage() != null ? item.getErrorMessage() : "");
                    
                    writeCsvRow(writer, row);
                }
            }
            
            String absolutePath = filePath.toAbsolutePath().toString();
            log.info("실패 CSV 파일 저장 완료: {} ({}개 상품)", absolutePath, failureItems.size());
            return absolutePath;
            
        } catch (IOException e) {
            log.error("실패 CSV 파일 저장 실패", e);
            return null;
        }
    }
    
    /**
     * CSV 헤더 추출 (성공 아이템용)
     */
    private List<String> extractSuccessHeaders(List<ExcelUploadResult.SuccessItem> items,
                                               Map<Integer, Map<String, Object>> originalDataMap) {
        List<String> headers = new ArrayList<>();
        
        // 첫 번째 아이템의 원본 데이터에서 헤더 추출
        if (!items.isEmpty()) {
            Map<String, Object> firstData = originalDataMap.get(items.get(0).getRowNumber());
            if (firstData != null) {
                for (String key : firstData.keySet()) {
                    if (!"_rowNumber".equals(key)) {
                        headers.add(key);
                    }
                }
            }
        }
        
        headers.add("상품ID"); // 네이버 상품 ID 추가
        return headers;
    }
    
    /**
     * CSV 헤더 추출 (실패 아이템용)
     */
    private List<String> extractFailureHeaders(List<ExcelUploadResult.FailureItem> items,
                                              Map<Integer, Map<String, Object>> originalDataMap) {
        List<String> headers = new ArrayList<>();
        
        // 첫 번째 아이템의 원본 데이터에서 헤더 추출
        if (!items.isEmpty()) {
            Map<String, Object> firstData = originalDataMap.get(items.get(0).getRowNumber());
            if (firstData != null) {
                for (String key : firstData.keySet()) {
                    if (!"_rowNumber".equals(key)) {
                        headers.add(key);
                    }
                }
            }
        }
        
        return headers;
    }
    
    /**
     * CSV 행 작성
     */
    private void writeCsvRow(FileWriter writer, List<String> row) throws IOException {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                writer.write(",");
            }
            writer.write(escapeCsvValue(row.get(i)));
        }
        writer.write("\n");
    }
    
    /**
     * CSV 값 이스케이프 처리
     */
    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        
        // 쉼표, 따옴표, 줄바꿈이 포함된 경우 따옴표로 감싸고 내부 따옴표는 두 개로 변환
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
    
    /**
     * CSV 값 포맷팅
     */
    private String formatCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }
    
    /**
     * 네이버 API 응답에서 상품 ID 추출
     */
    @SuppressWarnings("unchecked")
    private String extractProductId(Object response) {
        if (response == null) {
            return null;
        }
        
        try {
            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                
                // originProductNo 또는 productId 필드 확인
                Object productId = responseMap.get("originProductNo");
                if (productId == null) {
                    productId = responseMap.get("productId");
                }
                if (productId == null) {
                    // originProduct 객체 내부 확인
                    Object originProduct = responseMap.get("originProduct");
                    if (originProduct instanceof Map) {
                        Map<String, Object> originProductMap = (Map<String, Object>) originProduct;
                        productId = originProductMap.get("originProductNo");
                    }
                }
                
                return productId != null ? productId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("상품 ID 추출 실패", e);
        }
        
        return null;
    }
}

