---
description: Genera un mensaje de commit automático y hace push al repositorio
---

Instrucción adicional del usuario: $ARGUMENTS

Primero revisá los cambios actuales del proyecto:

!git status
!git diff --staged
!git diff

Con esa información:

1. Analizá qué archivos cambiaron y en qué servicio (frontend/, backend/, ai/)
2. Si el usuario dio una instrucción adicional arriba, tenela en cuenta para el mensaje o para decidir qué commitear
3. Generá un mensaje de commit siguiendo el formato convencional:
   - `feat:` nueva funcionalidad
   - `fix:` corrección de error
   - `chore:` configuración o dependencias
   - `refactor:` reestructuración de código
   - `docs:` documentación
   - Ejemplos: `feat(ai): agregar endpoint de inferencia`, `fix(backend): corregir validación de JWT`
4. Si hay archivos que NO deberían commitearse (`.env`, builds, logs), avisame y excluílos

!git add .
!git commit -m "<mensaje generado>"
!git push

Al finalizar mostrá:
- ✅ Archivos commiteados
- ✅ Mensaje de commit usado
- ✅ Confirmación del push
