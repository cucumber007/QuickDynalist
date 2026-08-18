# Pull-first reconciliation

Pull-first reconciliation is a synchronization strategy where a system first reads the authoritative or upstream state, then merges that state into its local copy while preserving any local changes that still need to be applied.

In abstract terms, "pull-first" describes the order of operations: read remote state before deciding how local state should change. "Reconciliation" describes the merge step: compare remote records with local records, match records that represent the same entity, then update, create, or remove local records as needed.
