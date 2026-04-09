begin;

delete from execution_record
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from approval_record
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from action_recommendation
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from diagnosis_task
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from diagnosis_record
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from postmortem_record
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from incident_evidence_item
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from incident_avoided_action
where incident_id in (
    select id from incident_event
    where incident_no like 'HG-PG-ZH-%'
       or incident_no like 'manual-%'
);

delete from incident_event
where incident_no like 'HG-PG-ZH-%'
   or incident_no like 'manual-%';

commit;
