package uk.co.real_logic.sbe.generation.rust;

import org.agrona.generation.OutputManager;

import java.io.IOException;
import java.io.Writer;

import static java.lang.String.format;
import static uk.co.real_logic.sbe.generation.rust.RustGenerator.DATA_LIFETIME;
import static uk.co.real_logic.sbe.generation.rust.RustUtil.INDENT;
import static uk.co.real_logic.sbe.generation.rust.RustUtil.indent;

enum RustCodecType
{
    Decoder
    {
        String scratchProperty()
        {
            return RustGenerator.SCRATCH_DECODER_PROPERY;
        }

        String scratchType()
        {
            return RustGenerator.SCRATCH_DECODER_TYPE;
        }

        void appendDirectCodeMethods(
            final Appendable appendable,
            final String methodName,
            final String representationType,
            final String nextCoderType,
            final int numBytes) throws IOException
        {
            indent(appendable, 1, "pub fn %s(mut self) -> CodecResult<(&%s %s, %s)> {\n",
                methodName, DATA_LIFETIME, representationType,
                RustGenerator.withLifetime(nextCoderType));
            indent(appendable, 2, "let v = self.%s.read_type::<%s>(%s)?;\n",
                RustCodecType.Decoder.scratchProperty(), representationType, numBytes);
            indent(appendable, 2, "Ok((v, %s::wrap(self.%s)))\n",
                nextCoderType, RustCodecType.Decoder.scratchProperty());
            indent(appendable).append("}\n");
        }

        String gerund()
        {
            return "decoding";
        }
    },

    Encoder
    {
        String scratchProperty()
        {
            return RustGenerator.SCRATCH_ENCODER_PROPERY;
        }

        String scratchType()
        {
            return RustGenerator.SCRATCH_ENCODER_TYPE;
        }

        void appendDirectCodeMethods(
            final Appendable appendable,
            final String methodName,
            final String representationType,
            final String nextCoderType,
            final int numBytes) throws IOException
        {
            indent(appendable, 1, "pub fn %s(mut self) -> CodecResult<(&%s mut %s, %s)> {\n",
                methodName, DATA_LIFETIME, representationType, RustGenerator.withLifetime(nextCoderType));
            indent(appendable, 2, "let v = self.%s.writable_overlay::<%s>(%s)?;\n",
                RustCodecType.Encoder.scratchProperty(), representationType, numBytes);
            indent(appendable, 2, "Ok((v, %s::wrap(self.%s)))\n",
                nextCoderType, RustCodecType.Encoder.scratchProperty());
            indent(appendable).append("}\n");

            indent(appendable).append(String.format("pub fn %s_copy(mut self, t: &%s) -> CodecResult<%s> {\n",
                methodName, representationType, RustGenerator.withLifetime(nextCoderType)));
            indent(appendable, 2)
                .append(format("self.%s.write_type::<%s>(t, %s)?;\n",
                    RustCodecType.Encoder.scratchProperty(), representationType, numBytes));
            indent(appendable, 2).append(format("Ok(%s::wrap(self.%s))\n",
                nextCoderType, RustCodecType.Encoder.scratchProperty()));
            indent(appendable).append("}\n");
        }

        String gerund()
        {
            return "encoding";
        }
    };

    void appendScratchWrappingStruct(final Appendable appendable, final String structName)
        throws IOException
    {
        appendable.append(String.format("pub struct %s <%s> {\n", structName, DATA_LIFETIME))
            .append(INDENT).append(String.format("%s: %s <%s>,%n", scratchProperty(), scratchType(), DATA_LIFETIME))
            .append("}\n");
    }

    abstract String scratchProperty();

    abstract String scratchType();

    abstract void appendDirectCodeMethods(Appendable appendable, String methodName,
        String representationType, String nextCoderType,
        int numBytes) throws IOException;

    abstract String gerund();

    String generateDoneCoderType(
        final OutputManager outputManager,
        final String messageTypeName)
        throws IOException
    {
        final String doneTypeName = messageTypeName + name() + "Done";
        try (Writer writer = outputManager.createOutput(doneTypeName))
        {
            appendScratchWrappingStruct(writer, doneTypeName);
            RustGenerator.appendImplWithLifetimeHeader(writer, doneTypeName);
            writer.append(INDENT).append(String.format("pub fn unwrap(mut self) -> (usize, &%s%s [u8]) {\n",
                DATA_LIFETIME, this == Encoder ? " mut" : ""))
                .append(INDENT).append(INDENT).append(format("(self.%s.pos, self.%s.data)\n",
                scratchProperty(), scratchProperty()))
                .append(INDENT).append("}\n");

            appendWrapMethod(writer, doneTypeName);
            writer.append("}\n");
        }
        return doneTypeName;
    }

    void appendWrapMethod(final Appendable appendable, final String structName)
        throws IOException
    {
        appendable.append("\n").append(INDENT).append(String.format(
            "fn wrap(%s: %s) -> %s {%n", scratchProperty(), RustGenerator.withLifetime(scratchType()),
            RustGenerator.withLifetime(structName)));
        indent(appendable, 2, "%s { %s: %s }\n",
            structName, scratchProperty(), scratchProperty());
        indent(appendable).append("}\n");
    }

    String generateMessageHeaderCoder(
        final String messageTypeName,
        final OutputManager outputManager,
        final String topType,
        final int headerSize) throws IOException
    {
        final String messageHeaderRepresentation = "MessageHeader";
        final String headerCoderType = messageTypeName + messageHeaderRepresentation + name();
        try (Writer writer = outputManager.createOutput(headerCoderType))
        {
            appendScratchWrappingStruct(writer, headerCoderType);
            RustGenerator.appendImplWithLifetimeHeader(writer, headerCoderType);
            appendWrapMethod(writer, headerCoderType);
            appendDirectCodeMethods(writer, "header", messageHeaderRepresentation, topType, headerSize);
            writer.append("}\n");
        }

        return headerCoderType;
    }
}
