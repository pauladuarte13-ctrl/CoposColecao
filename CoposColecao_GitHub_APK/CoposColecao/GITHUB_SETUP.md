# Publicar CoposColecao no GitHub e gerar APK automaticamente

Este projeto já inclui o workflow `.github/workflows/build-apk.yml`.

## Publicar no GitHub

1. Crie um repositório vazio chamado `CoposColecao` no GitHub.
2. Na pasta do projeto, execute:

```bash
git init
git add .
git commit -m "Initial commit: CoposColecao"
git branch -M main
git remote add origin https://github.com/SEU_UTILIZADOR/CoposColecao.git
git push -u origin main
```

## APK automática

A cada push para `main` ou `master`, o GitHub Actions:

- prepara JDK 17;
- instala Gradle 8.9;
- executa `gradle :app:assembleDebug`;
- publica `app-debug.apk` como artifact chamado `CoposColecao-debug-apk`.

No GitHub, abra **Actions** > **Build Android APK** > execução mais recente > **Artifacts** para descarregar a APK.

Também pode executar manualmente em **Actions** > **Build Android APK** > **Run workflow**.
