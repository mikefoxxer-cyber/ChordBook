import MessageType.*
import javax.sound.midi.Instrument

class ErrorMessage(val m: String) : Throwable(m)

interface MessageCreator {
    fun send(msg: Message, meToo: Boolean = false)
}

interface MessageListener {
    fun receive(o: MessageCreator, msg: Message)
}

enum class MessageType {
    AltTuningRequest, AnalysisRequest, ArpeggiateRequest, ItemAnalysisRequest, BackRequest,
    BassChange, ChordInstanceChange, ChordIndexChange, ChordListChange, ChordTypeChange,
    DelayChange, DurationChange, FileChange, FwdRequest, InstrumentChange, IntervalsChange,
    InversionChange, LegatoChange, ModeChange, MuteRequest, OptionsChange, PaintRequest,
    PlayRequest, RatiosChange, RootChange, SoloRequest,
    StopRequest, Swing8Change, SwingDot8Change, TempoChange, TickChange, TransposeChange,
    TuneToRequest, VolumeChange
}

open class Message(val t: MessageType) {
    override fun toString(): String {
        return "$t"
    }
}

class AltTuningRequestMessage : Message(AltTuningRequest)
class AnalysisRequestMessage(val pitches: List<Pitch>?) : Message(AnalysisRequest)
class ArpeggiateRequestMessage : Message(ArpeggiateRequest)
class ItemAnalysisRequestMessage(val item: Item) : Message(ItemAnalysisRequest)
class BackRequestMessage : Message(BackRequest)
class BassChangeMessage(val newVal: Note?) : Message(BassChange)
class ChordInstanceChangeMessage(val newVal: ChordInstance?) : Message(ChordInstanceChange)
class ChordIndexChangeMessage(val item: Item) : Message(ChordIndexChange)
class ChordListChangeMessage : Message(ChordListChange)
class ChordTypeChangeMessage(val newVal: Chord?) : Message(ChordTypeChange)
class DelayChangeMessage(val newVal: Long) : Message(DelayChange)
class DurationChangeMessage(val newVal: Long) : Message(DurationChange)
class FileChangeMessage : Message(FileChange)
class FwdRequestMessage : Message(FwdRequest)
class InstrumentChangeMessage(val newVal: Instrument?) : Message(InstrumentChange)
class IntervalsChangeMessage : Message(IntervalsChange)
class InversionChangeMessage(val newVal: Int) : Message(InversionChange)
class LegatoChangeMessage : Message(LegatoChange)
class ModeChangeMessage : Message(ModeChange)
class MuteRequestMessage(val vp: VoicePart?) : Message(MuteRequest)
class OptionsChangeMessage : Message(OptionsChange)
class PaintRequestMessage : Message(PaintRequest)
class PlayRequestMessage : Message(PlayRequest)
class RatiosChangeMessage : Message(RatiosChange)
class RootChangeMessage(val newVal: Note?) : Message(RootChange)
class SoloRequestMessage(val vp: VoicePart?) : Message(SoloRequest)
class StopRequestMessage : Message(StopRequest)
class Swing8ChangeMessage(val newVal: Boolean) : Message(Swing8Change)
class SwingDot8ChangeMessage(val newVal: Boolean) : Message(SwingDot8Change)
class TempoChangeMessage(val newVal: Int) : Message(TempoChange)
class TickChangeMessage(val text: String) : Message(TickChange)
class TransposeChangeMessage(val up: Boolean, val amount: Interval) : Message(TransposeChange)
class TuneToRequestMessage : Message(TuneToRequest)
class VolumeChangeMessage(val newVal: Int) : Message(VolumeChange)
