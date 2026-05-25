---
description: Lee los logs de Docker de los servicios frontend, backend e ia de AURA y detecta errores
---

Ejecutá los siguientes comandos de shell para obtener los logs recientes de cada servicio de AURA:

```shell
docker compose logs --tail=100 frontend
```
/;
```shell
docker compose logs --tail=100 backend
```

```shell
docker compose logs --tail=100 ai
```

Una vez obtenidos los logs de los tres servicios:

1. Analizá cada uno en busca de errores, warnings o excepciones
2. Indicá en qué servicio encontraste el problema
3. Mostrá las líneas relevantes del error
4. Explicá la causa probable
5. Proponé una solución concreta
