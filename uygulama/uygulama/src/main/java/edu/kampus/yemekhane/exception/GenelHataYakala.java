package edu.kampus.yemekhane.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.Map;

@RestControllerAdvice
public class GenelHataYakala {

    @ExceptionHandler(IsKuraliHatasi.class)
    public ResponseEntity> isHatasiYakala(IsKuraliHatasi ex) {
        return ResponseEntity.status(ex.getDurumKodu())
                .body(Map.of("hata", ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity> veritabaniHatasiYakala(DataAccessException ex) {
        Throwable kok = ex.getMostSpecificCause();
        if (kok instanceof SQLException sqlEx) {
            if ("45000".equals(sqlEx.getSQLState()) || sqlEx.getErrorCode() == 1644) {
                return ResponseEntity.badRequest().body(Map.of("hata", sqlEx.getMessage()));
            }
            if (sqlEx.getErrorCode() == 3819) {
                return ResponseEntity.badRequest().body(Map.of("hata", "Girilen değer kurallara uygun değil."));
            }
        }
        return ResponseEntity.internalServerError()
                .body(Map.of("hata", "Veritabanı işlemi sırasında beklenmeyen bir hata oluştu."));
    }
}
