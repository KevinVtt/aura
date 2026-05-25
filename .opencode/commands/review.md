---
description: Revisa el código de AURA antes de hacer un commit — calidad, errores y mejoras
---

Hacé una revisión completa del código modificado en AURA. Ejecutá primero:

!git diff --staged
!git diff

Con los cambios detectados, revisá lo siguiente para cada servicio afectado:

**Calidad del código**
- ¿Hay lógica duplicada o que se pueda simplificar?
- ¿Se respetan las convenciones del servicio (frontend/backend/ai)?
- ¿Los nombres de variables y funciones son claros?

**Posibles errores**
- ¿Hay errores evidentes o casos no manejados?
- ¿Las validaciones son suficientes?
- ¿Hay algún console.log, print() o debug code que no debería estar?

**Seguridad**
- ¿Hay credenciales, API keys o secrets hardcodeados?
- ¿Los inputs del usuario están validados?

**Impacto entre servicios**
- ¿Este cambio afecta el contrato de alguna API entre frontend, backend o ai?
- ¿Hay algo que pueda romper otro servicio?

Al final mostrá un resumen con:
- ✅ Lo que está bien
- ⚠️ Sugerencias de mejora
- ❌ Problemas que hay que corregir antes de commitear
