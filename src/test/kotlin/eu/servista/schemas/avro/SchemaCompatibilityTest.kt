package eu.servista.schemas.avro

import io.kotest.matchers.shouldBe
import java.io.File
import org.apache.avro.Schema
import org.apache.avro.SchemaCompatibility
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.junit.jupiter.api.Test

class SchemaCompatibilityTest {
    @Test
    fun `AccountCreated adding nullable field is BACKWARD compatible`() {
        val v1 = Schema.Parser().parse(File("src/main/avro/accounts/AccountCreated.avsc"))

        val v2 =
            Schema.Parser()
                .parse(
                    """
                    {"type":"record","name":"AccountCreated",
                     "namespace":"eu.servista.schemas.avro.accounts",
                     "fields":[
                       {"name":"account_id","type":"long"},
                       {"name":"email","type":"string"},
                       {"name":"display_name","type":"string"},
                       {"name":"organization_id","type":"long"},
                       {"name":"created_at","type":"long"},
                       {"name":"roles","type":["null","string"],"default":null}
                     ]}
                    """
                        .trimIndent()
                )

        // BACKWARD: new reader (v2) can read old data (v1)
        val result = SchemaCompatibility.checkReaderWriterCompatibility(v2, v1)
        result.type shouldBe SchemaCompatibilityType.COMPATIBLE
    }

    @Test
    fun `AccountCreated adding required field without default is NOT BACKWARD compatible`() {
        val v1 = Schema.Parser().parse(File("src/main/avro/accounts/AccountCreated.avsc"))

        // v2 adds a required field (no default) -- old data (v1) won't have this field,
        // so the new reader (v2) cannot read old data
        val v2 =
            Schema.Parser()
                .parse(
                    """
                    {"type":"record","name":"AccountCreated",
                     "namespace":"eu.servista.schemas.avro.accounts",
                     "fields":[
                       {"name":"account_id","type":"long"},
                       {"name":"email","type":"string"},
                       {"name":"display_name","type":"string"},
                       {"name":"organization_id","type":"long"},
                       {"name":"created_at","type":"long"},
                       {"name":"phone_number","type":"string"}
                     ]}
                    """
                        .trimIndent()
                )

        // BACKWARD: new reader (v2) tries to read old data (v1) -- fails because v1 has no
        // phone_number
        val result = SchemaCompatibility.checkReaderWriterCompatibility(v2, v1)
        result.type shouldBe SchemaCompatibilityType.INCOMPATIBLE
    }

    @Test
    fun `EventEnvelope adding nullable field is FULL compatible`() {
        val v1 = Schema.Parser().parse(File("src/main/avro/envelope/EventEnvelope.avsc"))

        val v2 =
            Schema.Parser()
                .parse(
                    """
                    {"type":"record","name":"EventEnvelope",
                     "namespace":"eu.servista.schemas.avro.envelope",
                     "fields":[
                       {"name":"event_id","type":"long"},
                       {"name":"event_type","type":"string"},
                       {"name":"aggregate_id","type":"long"},
                       {"name":"organization_id","type":"long"},
                       {"name":"account_id","type":["null","long"],"default":null},
                       {"name":"timestamp","type":"long"},
                       {"name":"correlation_id","type":"string"},
                       {"name":"causation_id","type":["null","long"],"default":null},
                       {"name":"payload","type":"bytes"},
                       {"name":"source_service","type":["null","string"],"default":null}
                     ]}
                    """
                        .trimIndent()
                )

        // FULL = both forward (old reader reads new data) and backward (new reader reads old data)
        val backward = SchemaCompatibility.checkReaderWriterCompatibility(v2, v1)
        backward.type shouldBe SchemaCompatibilityType.COMPATIBLE

        val forward = SchemaCompatibility.checkReaderWriterCompatibility(v1, v2)
        forward.type shouldBe SchemaCompatibilityType.COMPATIBLE
    }

    @Test
    fun `EventEnvelope removing required field is NOT FULL compatible`() {
        val v1 = Schema.Parser().parse(File("src/main/avro/envelope/EventEnvelope.avsc"))

        // v2 removes correlation_id (required field)
        val v2 =
            Schema.Parser()
                .parse(
                    """
                    {"type":"record","name":"EventEnvelope",
                     "namespace":"eu.servista.schemas.avro.envelope",
                     "fields":[
                       {"name":"event_id","type":"long"},
                       {"name":"event_type","type":"string"},
                       {"name":"aggregate_id","type":"long"},
                       {"name":"organization_id","type":"long"},
                       {"name":"account_id","type":["null","long"],"default":null},
                       {"name":"timestamp","type":"long"},
                       {"name":"causation_id","type":["null","long"],"default":null},
                       {"name":"payload","type":"bytes"}
                     ]}
                    """
                        .trimIndent()
                )

        // Forward check: old reader (v1) tries to read new data (v2) -- will fail because v1
        // expects correlation_id
        val forward = SchemaCompatibility.checkReaderWriterCompatibility(v1, v2)
        forward.type shouldBe SchemaCompatibilityType.INCOMPATIBLE
    }
}
