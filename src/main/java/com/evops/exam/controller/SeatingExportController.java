package com.evops.exam.controller;

import com.evops.exam.service.SeatingExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;

/**
 * 座次表导出：打印页（HTML，每考场分页）与 CSV 下载。路径在 /api/** 下走 HTTP Basic。
 */
@Controller
@RequestMapping("/api/arrange")
public class SeatingExportController {

    private final SeatingExportService seatingExportService;

    public SeatingExportController(SeatingExportService seatingExportService) {
        this.seatingExportService = seatingExportService;
    }

    /** 打印座次表：含考场号、座位号、证号、姓名、学校；浏览器内直接打印。 */
    @GetMapping(value = "/seating-chart", produces = MediaType.TEXT_HTML_VALUE)
    public String seatingChart(@RequestParam Long batchId, Model model) {
        model.addAttribute("chart", seatingExportService.buildChart(batchId));
        return "seating-chart";
    }

    /** 同一座次表的 CSV 版本，便于存档 / 二次排版。 */
    @GetMapping(value = "/seating-chart.csv")
    public ResponseEntity<byte[]> seatingChartCsv(@RequestParam Long batchId) {
        byte[] body = seatingExportService.buildCsv(batchId);
        String fileName;
        try {
            fileName = URLEncoder.encode("zuoweibiao-" + batchId + ".csv", "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            fileName = "seating-chart-" + batchId + ".csv";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"seating-chart-" + batchId + ".csv\"; filename*=UTF-8''" + fileName);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
