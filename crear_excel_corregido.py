#!/usr/bin/env python3
import pandas as pd
from datetime import datetime
import os

# Crear DataFrames
usuarios_df = pd.DataFrame([
    [1, 'Usuario A'],
    [2, 'Usuario B'],
    [3, 'Usuario C']
], columns=['ID', 'Nombre'])

fichadas_df = pd.DataFrame([
    [1, '19/01/2026 08:00:00', 1],
    [2, '19/01/2026 16:30:00', 1],
    [3, '19/01/2026 09:30:00', 2],
    [4, '19/01/2026 17:45:00', 2],
    [5, '19/01/2026 07:15:00', 3],
    [6, '19/01/2026 15:20:00', 3]
], columns=['ID', 'FechaHora', 'UserID'])

# Convertir a datetime
fichadas_df['FechaHora'] = pd.to_datetime(fichadas_df['FechaHora'], format='%d/%m/%Y %H:%M:%S')

# Crear Excel con dos hojas
with pd.ExcelWriter('datos_corregido.xlsx', engine='openpyxl', datetime_format='dd/mm/yyyy HH:mm:ss') as writer:
    usuarios_df.to_excel(writer, sheet_name='usuarios', index=False)
    fichadas_df.to_excel(writer, sheet_name='fichadas', index=False)

print("✅ Excel corregido creado: datos_corregido.xlsx")
print("\n📊 Resumen de datos:")
print(f"👥 Usuarios: {len(usuarios_df)}")
print(f"🕐 Fichadas: {len(fichadas_df)}")
print(f"📅 Fecha: 19/01/2026")

print("\n📋 Detalle de fichadas:")
for _, row in fichadas_df.iterrows():
    entrada_salida = "Entrada" if row['ID'] % 2 == 1 else "Salida"
    print(f"  Usuario {row['UserID']}: {entrada_salida} - {row['FechaHora'].strftime('%H:%M')}")

print("\n⏰ Verificación de ventanas:")
for _, row in fichadas_df.iterrows():
    hora = row['FechaHora'].time()
    es_entrada = row['ID'] % 2 == 1
    
    if es_entrada:
        ventana_ok = hora >= pd.Timestamp('05:00').time() and hora <= pd.Timestamp('14:00').time()
        print(f"  Entrada {hora.strftime('%H:%M')}: {'✅' if ventana_ok else '❌'} (ventana 05:00-14:00)")
    else:
        ventana_ok = hora >= pd.Timestamp('14:01').time()
        print(f"  Salida {hora.strftime('%H:%M')}: {'✅' if ventana_ok else '❌'} (ventana >=14:01)")

print("\n🎯 Todas las fichadas deberían aparecer como COMPLETAS (OK)")