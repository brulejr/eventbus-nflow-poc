create table if not exists workflow_ticket (
    ticket_id varchar(64) primary key,
    workflow_type varchar(128) not null,
    correlation_id varchar(128) not null,
    source varchar(64) not null,
    nflow_instance_id bigint,
    status varchar(64) not null,
    payload_json text,
    result_json text,
    error_message text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_workflow_ticket_correlation_id on workflow_ticket(correlation_id);
create index if not exists idx_workflow_ticket_nflow_instance_id on workflow_ticket(nflow_instance_id);
create index if not exists idx_workflow_ticket_status on workflow_ticket(status);
