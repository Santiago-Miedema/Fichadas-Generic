package org.example.service;

import org.example.Fichada;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Cliente para emular Control iD leyendo desde Excel:
 * - Reemplaza la conexión al lector biometrico por lectura de archivo Excel
 * - Mantiene la misma interfaz que ControlIdClient
 * - Estructura Excel esperada:
 *   * Hoja "usuarios": columnas ID (A), Nombre (B)
 *   * Hoja "fichadas": columnas ID (A), FechaHora (B), UserID (C)
 */
public class ControlIdClientExcel implements IControlIdClient {

    private final String excelFilePath;
    private final DateTimeFormatter excelDateTimeFormatter;
    
    /** Ajuste horario en minutos (Argentina: 180). 0 si el Excel ya está en hora local. */
    private static final int TIME_OFFSET_MIN = 180;

    public ControlIdClientExcel(String excelFilePath) {
        this.excelFilePath = excelFilePath;
        this.excelDateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    }

    /* ======================== Login ======================== */
    
    public boolean login(String user, String pass) {
        // Para emulación, siempre retorna true si el archivo Excel existe
        System.out.println("=== ControlIdClientExcel.login() ===");
        File excelFile = new File(excelFilePath);
        System.out.println("Buscando archivo Excel en: " + excelFile.getAbsolutePath());
        System.out.println("Directorio de trabajo actual: " + System.getProperty("user.dir"));
        System.out.println("¿Archivo existe? " + excelFile.exists());
        System.out.println("¿Archivo es legible? " + excelFile.canRead());
        
        try (FileInputStream fis = new FileInputStream(excelFilePath)) {
            System.out.println("✅ Archivo Excel abierto correctamente");
            Workbook workbook = new XSSFWorkbook(fis);
            System.out.println("✅ Workbook creado correctamente");
            workbook.close();
            System.out.println("✅ Login exitoso - usando Excel");
            return true;
        } catch (IOException e) {
            System.out.println("❌ Error al abrir archivo Excel: " + e.getMessage());
            System.out.println("❌ Archivo no encontrado o no accesible");
            return false;
        }
    }

    /* ======================== Users ======================== */

    /** Devuelve mapa id->nombre desde la hoja "usuarios" del Excel */
    public Map<Long, String> fetchUsersMap() {
        Map<Long, String> users = new HashMap<>();
        
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("usuarios");
            if (sheet == null) {
                System.out.println("Hoja 'usuarios' no encontrada en el Excel");
                return users;
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar encabezado
                
                Cell idCell = row.getCell(0);
                Cell nameCell = row.getCell(1);
                
                if (idCell != null && nameCell != null) {
                    long id = getNumericValue(idCell);
                    String name = getStringValue(nameCell);
                    users.put(id, name);
                }
            }
            
        } catch (IOException e) {
            System.out.println("Error leyendo usuarios desde Excel: " + e.getMessage());
        }
        
        return users;
    }

    /* ======================== Access Logs ======================== */

    /** Trae fichadas entre fechas desde la hoja "fichadas" del Excel */
    public List<Fichada> fetchAccessLogs(LocalDate from, LocalDate to) throws Exception {
        System.out.println("=== ControlIdClientExcel.fetchAccessLogs() ===");
        System.out.println("Buscando fichadas desde " + from + " hasta " + to);
        System.out.println("Archivo Excel: " + new File(excelFilePath).getAbsolutePath());
        
        List<Fichada> fichadas = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            System.out.println("✅ Leyendo fichadas desde Excel");
            
            Sheet sheet = workbook.getSheet("fichadas");
            if (sheet == null) {
                System.out.println("Hoja 'fichadas' no encontrada en el Excel");
                return fichadas;
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar encabezado
                
                Cell idCell = row.getCell(0);
                Cell dateTimeCell = row.getCell(1);
                Cell userIdCell = row.getCell(2);
                
                if (idCell != null && dateTimeCell != null) {
                    long id = getNumericValue(idCell);
                    LocalDateTime dateTime = getDateTimeValue(dateTimeCell);
                    Long userId = userIdCell != null ? getNumericValue(userIdCell) : null;
                    
                    if (dateTime != null) {
                        LocalDate fichadaDate = dateTime.toLocalDate();
                        if (!fichadaDate.isBefore(from) && !fichadaDate.isAfter(to)) {
                            fichadas.add(new Fichada(id, dateTime, userId));
                        }
                    }
                }
            }
            
        } catch (IOException e) {
            System.out.println("Error leyendo fichadas desde Excel: " + e.getMessage());
            throw e;
        }
        
        // Ordenar igual que el original
        fichadas.sort(Comparator
                .comparing(Fichada::userId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(Fichada::dateTime));
        
        return fichadas;
    }

    /* ======================== Helper methods ======================== */

    private long getNumericValue(Cell cell) {
        switch (cell.getCellType()) {
            case NUMERIC:
                return (long) cell.getNumericCellValue();
            case STRING:
                try {
                    return Long.parseLong(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            case FORMULA:
                return (long) cell.getNumericCellValue();
            default:
                return 0;
        }
    }

    private String getStringValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case FORMULA:
                return String.valueOf(cell.getNumericCellValue());
            default:
                return "";
        }
    }

    private LocalDateTime getDateTimeValue(Cell cell) {
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        Date date = cell.getDateCellValue();
                        return date.toInstant()
                                .plusSeconds(TIME_OFFSET_MIN * 60L)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime();
                    }
                    break;
                case STRING:
                    String dateStr = cell.getStringCellValue().trim();
                    return LocalDateTime.parse(dateStr, excelDateTimeFormatter)
                            .plusMinutes(TIME_OFFSET_MIN);
            }
        } catch (Exception e) {
            System.out.println("Error parseando fecha: " + e.getMessage());
        }
        return null;
    }
}