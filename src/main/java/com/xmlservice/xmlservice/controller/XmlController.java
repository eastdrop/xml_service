package com.xmlservice.xmlservice.controller;

import com.xmlservice.xmlservice.service.XmlParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/xml")
@RequiredArgsConstructor
@Slf4j
public class XmlController {

    private final XmlParserService xmlParserService;

    @GetMapping("/source-info")
    public ResponseEntity<?> getSourceInfo() {
        Map<String, Object> info = xmlParserService.getXmlSourceInfo();
        return ResponseEntity.ok(info);
    }

    @PostMapping("/clear-cache")
    public ResponseEntity<?> clearCache() {
        xmlParserService.clearCache();
        return ResponseEntity.ok(Map.of(
                "message", "XML cache cleared successfully",
                "status", "success"
        ));
    }

    @GetMapping("/content")
    public ResponseEntity<?> getXmlContent(
            @RequestParam(defaultValue = "false") boolean raw,
            @RequestParam(defaultValue = "1000") int maxLength) {

        try {
            String xmlContent = xmlParserService.fetchXmlContent();

            if (raw) {
                return ResponseEntity.ok(xmlContent);
            } else {
                String preview = xmlContent.length() > maxLength
                        ? xmlContent.substring(0, maxLength) + "..."
                        : xmlContent;

                return ResponseEntity.ok(Map.of(
                        "length", xmlContent.length(),
                        "preview", preview,
                        "fullLength", xmlContent.length()
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to fetch XML content",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateXml() {
        try {
            String xmlContent = xmlParserService.fetchXmlContent();
            var doc = xmlParserService.parseXml(xmlContent);

            // Проверка структуры
            var root = doc.getDocumentElement();
            var shop = (org.w3c.dom.Element) doc.getElementsByTagName("shop").item(0);
            var offers = doc.getElementsByTagName("offer");
            var categories = doc.getElementsByTagName("category");
            var currencies = doc.getElementsByTagName("currency");

            return ResponseEntity.ok(Map.of(
                    "isValid", true,
                    "rootElement", root.getNodeName(),
                    "shopExists", shop != null,
                    "offersCount", offers.getLength(),
                    "categoriesCount", categories.getLength(),
                    "currenciesCount", currencies.getLength(),
                    "message", "XML is valid and well-formed"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "isValid", false,
                    "error", e.getMessage(),
                    "message", "XML validation failed"
            ));
        }
    }
}