-- Fase 0.1 — script de auditoría de solo lectura para el SQL Editor de Supabase.
-- No modifica nada (solo SELECTs contra catálogos del sistema). Generado para contrastar
-- el proyecto real contra agent-docs/supabase-sync/03-esquema-supabase.md.
-- Pega la salida de cada bloque en el chat, o guárdala en
-- agent-docs/supabase-sync/09-fase0-resultado.md.

-- 1) Tablas esperadas: existencia + RLS activado (relrowsecurity)
select c.relname as tabla,
       c.relrowsecurity as rls_activado,
       c.relforcerowsecurity as rls_forzado_para_owner
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('task_lists', 'tasks', 'subtasks', 'reminders')
order by c.relname;

-- 2) Columnas de cada tabla (contrastar contra doc 03 / 05)
select table_name, column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public'
  and table_name in ('task_lists', 'tasks', 'subtasks', 'reminders')
order by table_name, ordinal_position;

-- 3) Políticas RLS por tabla (esperado: select_own/insert_own/update_own/delete_own x4 tablas = 16)
select schemaname, tablename, policyname, cmd as operacion, roles, qual as using_expr, with_check
from pg_policies
where schemaname = 'public'
  and tablename in ('task_lists', 'tasks', 'subtasks', 'reminders')
order by tablename, cmd;

-- 4) Triggers instalados en las 4 tablas + función que ejecutan
select event_object_table as tabla, trigger_name, action_timing, event_manipulation, action_statement
from information_schema.triggers
where event_object_schema = 'public'
  and event_object_table in ('task_lists', 'tasks', 'subtasks', 'reminders')
order by event_object_table, trigger_name;

-- 5) Código fuente real de la función del trigger (para comparar byte a byte con doc 03)
select p.proname as funcion, pg_get_functiondef(p.oid) as definicion
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('touch_and_resolve_lww', 'purge_old_tombstones', 'roll_recurring_tasks', 'calculate_next_due_date');

-- 6) Jobs de pg_cron programados (esperado: purge-tombstones-daily y el de recurrencia)
select jobid, jobname, schedule, command, active
from cron.job
order by jobid;

-- 7) Tablas incluidas en la publicación de Realtime (esperado: las 4)
select schemaname, tablename
from pg_publication_tables
where pubname = 'supabase_realtime'
order by tablename;

-- 8) Bucket de Storage task-images + sus políticas
select id, name, public, file_size_limit, allowed_mime_types
from storage.buckets
where id = 'task-images';

select policyname, cmd as operacion, roles, qual as using_expr, with_check
from pg_policies
where schemaname = 'storage'
  and tablename = 'objects'
order by policyname;

-- 9) Extensiones requeridas activas
select extname, extversion
from pg_extension
where extname in ('pgcrypto', 'pg_cron', 'pg_net')
order by extname;
