Feature: Ingress tests

  Background:
    Given authenticate against Manager
    And create a new Bridge "mybridge"
    And the Bridge "mybridge" is existing with status "ready" within 4 minutes
    And the Ingress of Bridge "mybridge" is available within 2 minutes
    And add a Processor to the Bridge "mybridge" with body:
    """
    {
      "name": "myProcessor",
      "action": {
        "parameters": {
            "topic":  "myKafkaTopic"
        },
        "type": "KafkaTopic"
      }
    }
    """
    And the Processor "myProcessor" of the Bridge "mybridge" is existing with status "ready" within 3 minutes

  Scenario: Send Cloud Event
    When send a cloud event to the Ingress of the Bridge "mybridge":
    """
    {
    "specversion": "1.0",
    "type": "Microsoft.Storage.BlobCreated",
    "source": "StorageService",
    "id": "9aeb0fdf-c01e-0131-0922-9eb54906e209",
    "time": "2019-11-18T15:13:39.4589254Z",
    "subject": "blobServices/default/containers/{storage-container}/blobs/{new-file}",
    "dataschema": "#",
    "data": {
        "api": "PutBlockList"
      }
    }
    """
    Then the Ingress of the Bridge "mybridge" metric 'http_server_requests_seconds_count{method="POST",outcome="SUCCESS",status="200",uri="/events",}' count is at least 1

  Scenario: Send plain Cloud Event
    When send a cloud event to the Ingress of the Bridge "mybridge" with path "plain" and headers "ce-id":"my-id","ce-source":"mySource","ce-specversion":"1.0","ce-type":"myType":
    """
    { "data" : "test" }
    """
    Then the Ingress of the Bridge "mybridge" metric 'http_server_requests_seconds_count{method="POST",outcome="SUCCESS",status="200",uri="/events/plain",}' count is at least 1
