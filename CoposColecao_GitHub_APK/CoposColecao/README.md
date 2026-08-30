# Coleção de Copos

Aplicação Android offline para catalogar uma coleção de copos com marcas de bebidas.

## Funcionalidades incluídas

- Dados guardados no próprio dispositivo com Room/SQLite.
- Fotografias guardadas no armazenamento interno privado da aplicação.
- Número sequencial automático e não reutilizado após eliminações.
- Marca e descrição.
- Pesquisa por número, marca e descrição.
- Introdução de fotografia pela galeria ou pela câmara.
- Reconhecimento local por IA com MediaPipe Image Embedder + MobileNet V3.
- Comparação semântica por IA antes de gravar, inclusive com ângulos/fundos diferentes.
- Aviso de possíveis duplicados com percentagem de semelhança.
- Backup completo em ZIP (dados + fotografias).
- Restauro integral do backup, validado antes de substituir a coleção.
- Edição e eliminação de registos.

## Abrir no Android Studio

1. Abra a pasta `CoposColecao`.
2. Aguarde a sincronização do Gradle.
3. Execute num dispositivo Android 8.0 (API 26) ou superior.

## Reconhecimento por IA

A aplicação usa o MediaPipe Image Embedder com MobileNet V3 Small para representar cada fotografia
e calcular a similaridade de cosseno com os copos já existentes. A inferência acontece no dispositivo:
as fotografias não são enviadas para a Internet.

O modelo oficial é descarregado automaticamente pelo Gradle na primeira compilação e fica incorporado
no APK. Depois de instalada, a aplicação consegue fazer o reconhecimento sem ligação à Internet.

O dHash anterior mantém-se como metadado leve e pode ser usado futuramente como filtro rápido.

## Backup e restauro

O menu no topo permite criar `copos_backup.zip`. O ficheiro contém `manifest.json` e todas as fotografias.
O restauro valida o conteúdo e copia as novas fotografias antes de substituir a coleção atual.

## Próximos passos recomendados

- Guardar embeddings de IA na base de dados para acelerar coleções com milhares de copos.
- Filtros por marca e ordenação configurável.
- Campo opcional para país, tipo de bebida, ano e observações.
- Exportação da coleção para CSV/PDF.
