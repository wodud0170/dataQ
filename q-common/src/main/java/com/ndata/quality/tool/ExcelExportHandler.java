package com.ndata.quality.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.poi.hssf.record.ColumnInfoRecord;
import org.apache.poi.hssf.util.HSSFColor.HSSFColorPredefined;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import com.ndata.module.StringUtils;
import com.ndata.quality.service.ExcelDownloadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelExportHandler {

	private static final int MAX_ROW = 1040000;
	private static final int PAGING_SIZE = 10000;

	@SuppressWarnings("unchecked")
	public ByteArrayInputStream buildExcelDocument(Map<String, Object> excelMap, 
			HttpServletRequest request, HttpServletResponse response) throws Exception {

		List<String> keys = (List<String>) excelMap.get("keys");
		List<String> headers = (List<String>) excelMap.get("headers");
		List<String> widths = (List<String>) excelMap.get("widths");
		List<String> aligns = (List<String>) excelMap.get("aligns");
		List<Map<String, Object>> list = (List<Map<String, Object>>) excelMap.get("list");
		long listSize = list.size();
		String fileName = (String) excelMap.get("fileName");

		String userAgent = request.getHeader("User-Agent");
		log.info(">>User-Agent={}", userAgent);

		if (userAgent.contains("Trident") || (userAgent.indexOf("MSIE") > -1)) {
			fileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
		} else if (userAgent.contains("Chrome") || userAgent.contains("Opera") || userAgent.contains("Firefox")) {
			fileName = new String(fileName.getBytes("UTF-8"), "ISO-8859-1");
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		try {
			response.setContentType("application/octet-stream");//"application/vnd.ms-excel");
			response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

			SXSSFWorkbook sxssfWorkbook = null;
			List<Map<String, Object>> excelData = null;
			
			for (int start = 0; start < listSize; start += PAGING_SIZE) {
				excelData = getExcelList(list, start, PAGING_SIZE);
				sxssfWorkbook = getWorkbook(fileName, headers, keys, widths, aligns, excelData, start, sxssfWorkbook);
				sxssfWorkbook.write(output);
				output.flush();
				sxssfWorkbook.close();
				excelData.clear(); // 리스트 페이징 처리 및 메모리
			}

			return new ByteArrayInputStream(output.toByteArray());
		} catch (Exception e) {
			log.error("[SxssfExcelView] error message: {}", e.getMessage());
			return null;
		} finally {
			if (output != null) {
				output.close();
			}
		}
	}

	private SXSSFWorkbook getWorkbook(String fileName, List<String> headers, List<String> keys, List<String> widths,
			List<String> aligns, List<Map<String, Object>> list, int rowIdx, SXSSFWorkbook sxssfWorkbook) {
		// 최초 생성이면 manual flush를 위해 new SXSSFWorkbook(-1)
		// 이어서 작성일 경우 매개변수로 받은 sxssfWorkbook
		SXSSFWorkbook workbook = ObjectUtils.isNotEmpty(sxssfWorkbook) ? sxssfWorkbook : new SXSSFWorkbook(-1);
		// 최초 생성이면 SheetN 생성
		// 이어서 작성일 경우 SheetN에서 이어서
		String sheetName = "Sheet" + (rowIdx / MAX_ROW + 1); // 각 시트 당 1,040,000개씩
		boolean newSheet = ObjectUtils.isEmpty(workbook.getSheet(sheetName));
		Sheet sheet = ObjectUtils.isEmpty(workbook.getSheet(sheetName)) ? workbook.createSheet(sheetName)
				: workbook.getSheet(sheetName);

		CellStyle headerStyle = createHeaderStyle(workbook);
		CellStyle bodyStyleLeft = createBodyStyle(workbook, "LEFT");
		CellStyle bodyStyleRight = createBodyStyle(workbook, "RIGHT");
		CellStyle bodyStyleCenter = createBodyStyle(workbook, "CENTER");

		// \r\n을 통해 셀 내 개행
		// 개행을 위해 setWrapText 설정
		bodyStyleLeft.setWrapText(true);
		bodyStyleRight.setWrapText(true);
		bodyStyleCenter.setWrapText(true);

		int idx = 0;
		for (String width : widths) {
			sheet.setColumnWidth(idx++, Integer.parseInt(width) * 256);
		}

		Row row = null;
		Cell cell = null;

		// 매개변수로 받은 rowIdx % MAX_ROW 행부터 이어서 데이터
		int rowNo = rowIdx % MAX_ROW;

		if (newSheet) {
			row = sheet.createRow(rowNo);

			for (idx=0; idx<headers.size(); idx++) {
				String columnName = headers.get(idx);
				//columnName이 "M:"으로 시작하는 경우에 merged column 처리
				if (columnName.startsWith("M:")) {
					String[] mergeInfo = columnName.split(":");
					//merged row 생성
					cell = row.createCell(idx);
					cell.setCellStyle(headerStyle);
					cell.setCellValue(mergeInfo[2]);
					int mergeCnt = Integer.parseInt(mergeInfo[1]);
					sheet.addMergedRegion(new CellRangeAddress(0, 0, idx, idx + mergeCnt - 1));
					//1번 split row 생성
					Row splitRow = sheet.createRow(++rowNo);
					cell = splitRow.createCell(idx);
					cell.setCellStyle(headerStyle);
					cell.setCellValue(mergeInfo[3]);
					//나머지 split row 생성
					for (int i=1; i<mergeCnt; i++) {
						cell = splitRow.createCell(++idx);
						cell.setCellStyle(headerStyle);
						cell.setCellValue(headers.get(idx));
					}
					idx++;//merged column 이후부터 다시 헤더생성하도록 idx 증가
					//column merge : merged column 이전까지
					for (int i=0; i<(idx-mergeCnt); i++) {
						cell = splitRow.createCell(i);
						cell.setCellStyle(headerStyle);
						sheet.addMergedRegion(new CellRangeAddress(0, 1, i, i));
					}
					//column merge : merged column 이후부터 끝까지
					for (int i=idx; i<headers.size(); i++) {
						cell = splitRow.createCell(i);
						cell.setCellStyle(headerStyle);
						sheet.addMergedRegion(new CellRangeAddress(0, 1, i, i));
					}
				}
				cell = row.createCell(idx);
				cell.setCellStyle(headerStyle);
				cell.setCellValue(headers.get(idx));
			}
		}

		for (Map<String, Object> tempRow : list) {
			idx = 0;
			row = sheet.createRow(++rowNo);

			for (String key : keys) {
				if (StringUtils.isEmpty(key)) {
					continue;
				}

				cell = row.createCell(idx);

				if (ObjectUtils.isEmpty(aligns)) {
					// 디폴트 가운데 정렬
					cell.setCellStyle(bodyStyleCenter);
				} else {
					String hAlign = aligns.get(idx);

					if ("LEFT".equals(hAlign)) {
						cell.setCellStyle(bodyStyleLeft);
					} else if ("RIGHT".equals(hAlign)) {
						cell.setCellStyle(bodyStyleRight);
					} else {
						cell.setCellStyle(bodyStyleCenter);
					}
				}

				Object value = tempRow.get(key);

				if (value instanceof BigDecimal) {
					cell.setCellValue(((BigDecimal) value).toString());
				} else if (value instanceof Double) {
					cell.setCellValue(((Double) value).toString());
				} else if (value instanceof Long) {
					cell.setCellValue(((Long) value).toString());
				} else if (value instanceof Integer) {
					cell.setCellValue(((Integer) value).toString());
				} else if (value instanceof Short) {
					cell.setCellValue(((Short) value).toString());
				} else {
					cell.setCellValue((String) value);
				}

				idx++;

				// 주기적인 flush 진행
				if (rowNo % 100 == 0) {
					try {
						((SXSSFSheet) sheet).flushRows(100);
					} catch (IOException e) {
						// TODO Auto-generated catch block
					}
				}
			}
		}

		return workbook;
	}

	private CellStyle createHeaderStyle(Workbook workbook) {
		CellStyle headerStyle = createBodyStyle(workbook, "CENTER");
		// 취향에 따라 설정 가능
		headerStyle.setFillForegroundColor(HSSFColorPredefined.LIGHT_YELLOW.getIndex());
		headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

		// 가로 세로 정렬 기준
		headerStyle.setAlignment(HorizontalAlignment.CENTER);
		headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

		return headerStyle;
	}

	private CellStyle createBodyStyle(Workbook workbook, String align) {
		CellStyle bodyStyle = workbook.createCellStyle();
		// 취향에 따라 설정 가능
		bodyStyle.setBorderTop(BorderStyle.THIN);
		bodyStyle.setBorderBottom(BorderStyle.THIN);
		bodyStyle.setBorderLeft(BorderStyle.THIN);
		bodyStyle.setBorderRight(BorderStyle.THIN);
		bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);

		if (StringUtils.isEmpty(align) == false) {
			if ("LEFT".equals(align)) {
				bodyStyle.setAlignment(HorizontalAlignment.LEFT);
			} else if ("RIGHT".equals(align)) {
				bodyStyle.setAlignment(HorizontalAlignment.RIGHT);
			} else {
				bodyStyle.setAlignment(HorizontalAlignment.CENTER);
			}
		}

		return bodyStyle;
	}

    public static List<Map<String, Object>> getExcelList(List<Map<String, Object>> dataList, int start, int size) {
		// start 인덱스부터 size개의 데이터를 지닌 리스트 반환
		return dataList.stream().skip(start).limit(size).collect(Collectors.toList());
    }
}
