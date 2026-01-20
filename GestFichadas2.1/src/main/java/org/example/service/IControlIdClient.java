package org.example.service;

import org.example.Fichada;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Interfaz común para clientes de Control iD
 * Permite intercambiar entre cliente real (HTTP) y cliente Excel
 */
public interface IControlIdClient {
    
    /**
     * Autenticación con el cliente
     * @param user usuario
     * @param pass contraseña
     * @return true si autenticación exitosa
     */
    boolean login(String user, String pass);
    
    /**
     * Obtiene mapa de usuarios (id -> nombre)
     * @return mapa con usuarios, vacío si hay error
     */
    Map<Long, String> fetchUsersMap();
    
    /**
     * Obtiene fichadas entre fechas
     * @param from fecha desde
     * @param to fecha hasta
     * @return lista de fichadas ordenadas
     * @throws Exception si hay error de conexión/lectura
     */
    List<Fichada> fetchAccessLogs(LocalDate from, LocalDate to) throws Exception;
}