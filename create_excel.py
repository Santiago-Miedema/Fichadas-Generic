#!/usr/bin/env python3
import zipfile
import xml.etree.ElementTree as ET
from xml.dom import minidom
import os

def create_excel_xml():
    """Crear un Excel simple con la estructura correcta"""
    
    # Crear directorio temporal para el Excel
    os.makedirs('temp_excel/xl/worksheets', exist_ok=True)
    os.makedirs('temp_excel/xl/_rels', exist_ok=True)
    os.makedirs('temp_excel/_rels', exist_ok=True)
    os.makedirs('temp_excel/docProps', exist_ok=True)
    
    # 1. Crear [Content_Types].xml
    content_types = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats.spreadsheetml.sheet.main+xml"/>
    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats.spreadsheetml.worksheet+xml"/>
    <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats.spreadsheetml.worksheet+xml"/>
    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats.spreadsheetml.styles+xml"/>
    <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
    <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>'''
    
    with open('temp_excel/[Content_Types].xml', 'w') as f:
        f.write(content_types)
    
    # 2. Crear _rels/.rels
    rels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>'''
    
    with open('temp_excel/_rels/.rels', 'w') as f:
        f.write(rels)
    
    # 3. Crear workbook.xml
    workbook = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <sheets>
        <sheet name="usuarios" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
        <sheet name="fichadas" sheetId="2" r:id="rId2" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
    </sheets>
</workbook>'''
    
    with open('temp_excel/xl/workbook.xml', 'w') as f:
        f.write(workbook)
    
    # 4. Crear xl/_rels/workbook.xml.rels
    workbook_rels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>'''
    
    with open('temp_excel/xl/_rels/workbook.xml.rels', 'w') as f:
        f.write(workbook_rels)
    
    # 5. Crear hoja de usuarios (sheet1.xml)
    usuarios_sheet = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <sheetData>
        <row r="1">
            <c r="A1" t="inlineStr"><is><t>ID</t></is></c>
            <c r="B1" t="inlineStr"><is><t>Nombre</t></is></c>
        </row>
        <row r="2">
            <c r="A2" t="n"><v>1</v></c>
            <c r="B2" t="inlineStr"><is><t>Usuario A</t></is></c>
        </row>
        <row r="3">
            <c r="A3" t="n"><v>2</v></c>
            <c r="B3" t="inlineStr"><is><t>Usuario B</t></is></c>
        </row>
        <row r="4">
            <c r="A4" t="n"><v>3</v></c>
            <c r="B4" t="inlineStr"><is><t>Usuario C</t></is></c>
        </row>
    </sheetData>
</worksheet>'''
    
    with open('temp_excel/xl/worksheets/sheet1.xml', 'w') as f:
        f.write(usuarios_sheet)
    
    # 6. Crear hoja de fichadas (sheet2.xml) con fechas correctas
    # Usando valores numéricos de Excel para las fechas
    # 19/01/2026 = 46042
    # 08:00 = 0.3333333333333333
    # 16:30 = 0.6875
    # 09:30 = 0.3958333333333333
    # 17:45 = 0.7395833333333334
    # 07:15 = 0.3020833333333333
    # 15:20 = 0.6388888888888888
    
    fichadas_sheet = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <sheetData>
        <row r="1">
            <c r="A1" t="inlineStr"><is><t>ID</t></is></c>
            <c r="B1" t="inlineStr"><is><t>FechaHora</t></is></c>
            <c r="C1" t="inlineStr"><is><t>UserID</t></is></c>
        </row>
        <row r="2">
            <c r="A2" t="n"><v>1</v></c>
            <c r="B2" t="n"><v>46042.333333333333</v></c>
            <c r="C2" t="n"><v>1</v></c>
        </row>
        <row r="3">
            <c r="A3" t="n"><v>2</v></c>
            <c r="B3" t="n"><v>46042.6875</v></c>
            <c r="C3" t="n"><v>1</v></c>
        </row>
        <row r="4">
            <c r="A4" t="n"><v>3</v></c>
            <c r="B4" t="n"><v>46042.395833333333</v></c>
            <c r="C4" t="n"><v>2</v></c>
        </row>
        <row r="5">
            <c r="A5" t="n"><v>4</v></c>
            <c r="B5" t="n"><v>46042.739583333333</v></c>
            <c r="C5" t="n"><v>2</v></c>
        </row>
        <row r="6">
            <c r="A6" t="n"><v>5</v></c>
            <c r="B6" t="n"><v>46042.302083333333</v></c>
            <c r="C6" t="n"><v>3</v></c>
        </row>
        <row r="7">
            <c r="A7" t="n"><v>6</v></c>
            <c r="B7" t="n"><v>46042.638888888888</v></c>
            <c r="C7" t="n"><v>3</v></c>
        </row>
    </sheetData>
</worksheet>'''
    
    with open('temp_excel/xl/worksheets/sheet2.xml', 'w') as f:
        f.write(fichadas_sheet)
    
    # 7. Crear styles.xml básico
    styles = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <numFmts count="1">
        <numFmt numFmtId="164" formatCode="dd/mm/yyyy hh:mm:ss"/>
    </numFmts>
    <cellXfs count="2">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
        <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    </cellXfs>
</styleSheet>'''
    
    with open('temp_excel/xl/styles.xml', 'w') as f:
        f.write(styles)
    
    # 8. Crear docProps/core.xml
    core = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <dc:creator>GestFichadas</dc:creator>
    <dcterms:created xsi:type="dcterms:W3CDTF">2026-01-19T00:00:00Z</dcterms:created>
    <dc:title>datos corregido</dc:title>
</cp:coreProperties>'''
    
    with open('temp_excel/docProps/core.xml', 'w') as f:
        f.write(core)
    
    # 9. Crear docProps/app.xml
    app = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
    <Application>GestFichadas</Application>
    <DocSecurity>0</DocSecurity>
    <ScaleCrop>false</ScaleCrop>
    <SharedDoc>false</SharedDoc>
    <HyperlinksChanged>false</HyperlinksChanged>
    <AppVersion>1.0</AppVersion>
</Properties>'''
    
    with open('temp_excel/docProps/app.xml', 'w') as f:
        f.write(app)
    
    # 10. Crear el ZIP final
    with zipfile.ZipFile('datos_corregido.xlsx', 'w', zipfile.ZIP_DEFLATED) as zf:
        # Agregar todos los archivos
        for root, dirs, files in os.walk('temp_excel'):
            for file in files:
                file_path = os.path.join(root, file)
                arcname = os.path.relpath(file_path, 'temp_excel')
                zf.write(file_path, arcname)
    
    # Limpiar directorio temporal
    import shutil
    shutil.rmtree('temp_excel')
    
    print("✅ Excel corregido creado: datos_corregido.xlsx")
    print("\n📊 Datos incluidos:")
    print("👥 3 usuarios (Usuario A, B, C)")
    print("🕐 6 fichadas para 19/01/2026:")
    print("   Usuario 1: 08:00 → 16:30 (8.5 horas)")
    print("   Usuario 2: 09:30 → 17:45 (8.25 horas)")
    print("   Usuario 3: 07:15 → 15:20 (8.08 horas)")
    print("\n⏰ Todos los horarios están dentro de las nuevas ventanas:")
    print("   Entradas: 05:00-14:00 ✅")
    print("   Salidas: >=14:01 ✅")
    print("   Duraciones: 2-16 horas ✅")
    print("\n🎯 Todas las fichadas deberían aparecer como COMPLETAS (OK)")

if __name__ == "__main__":
    create_excel_xml()