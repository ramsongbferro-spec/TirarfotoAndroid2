# Tirar Foto por Voz (Android)

Aplicativo Android nativo em Kotlin. Quando reconhece a frase **“tirar foto”**, fotografa primeiro com a câmera traseira e depois com a frontal. As imagens são gravadas na galeria em `Pictures/TirarFoto`.

## Requisito
- Android 10 ou superior.

## Recursos
- Reconhecimento de voz em português do Brasil (`pt-BR`).
- Captura sequencial traseira + frontal, para funcionar em mais celulares do que uma solução que exija as duas câmeras abertas simultaneamente.
- Botão de voz para pausar/retomar a escuta.
- Botão para tirar as duas fotos manualmente.
- Fotos salvas localmente no aparelho; não há envio de fotos para servidor pelo app.
- Por privacidade, a escuta é interrompida quando o aplicativo sai de primeiro plano.

## Limitações importantes
- O app escuta o comando enquanto está aberto e visível; não é um assistente sempre ativo em segundo plano.
- O reconhecimento de voz depende de um serviço instalado/ativado no Android e pode precisar de internet, conforme a configuração do aparelho.
- A câmera frontal e a traseira são usadas em sequência, não no mesmo instante. Isso evita depender do suporte de câmera dupla simultânea do aparelho.

## Como compilar o APK

Abra esta pasta no Android Studio com Android SDK 35 e JDK 17 instalados e compile a variante `debug` (`app > Tasks > build > assembleDebug`). O APK de teste será criado em `app/build/outputs/apk/debug/app-debug.apk`.

Também há um fluxo GitHub Actions em `.github/workflows/build-apk.yml` que compila o APK quando o projeto é enviado para um repositório GitHub. O arquivo APK aparece como artefato da execução da ação.
