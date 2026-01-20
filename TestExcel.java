import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class TestExcel {
    public static void main(String[] args) {
        try {
            // Simular la lectura del Excel como lo hace ControlIdClientExcel
            FileInputStream fis = new FileInputStream("datos.xlsx");
            
            System.out.println("Archivo Excel encontrado y abierto correctamente");
            fis.close();
            
            System.out.println("Si ves este mensaje, el Excel es accesible.");
            System.out.println("El problema debe estar en el contenido del Excel o en cómo se ejecuta la aplicación.");
            
        } catch (Exception e) {
            System.out.println("Error al leer el Excel: " + e.getMessage());
            e.printStackTrace();
        }
    }
}