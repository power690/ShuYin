#ifdef IO_USE_WIN_FILE_IO

#pragma once

#include "IAPEIO.h"

namespace APE
{

class CWinFileIO : public IAPEIO
{
public:
    // construction / destruction
    CWinFileIO();
    ~CWinFileIO() APE_OVERRIDE;

    // open / close
    int Open(const str_utfn * pName, bool bOpenReadOnly = false) APE_OVERRIDE;
    int Close() APE_OVERRIDE;

    // read / write
    int Read(void * pBuffer, int64 nBytesToRead, int64 * pBytesRead) APE_OVERRIDE;
    int Write(const void * pBuffer, int64 nBytesToWrite, int64 * pBytesWritten) APE_OVERRIDE;

    // seek
    int Seek(int64 nPosition, SeekMethod nMethod) APE_OVERRIDE;

    // other functions
    int SetEOF() APE_OVERRIDE;
    unsigned char * GetBuffer(int *)  APE_OVERRIDE { return APE_NULL; }

    // creation / destruction
    int Create(const str_utfn * pName) APE_OVERRIDE;
    int Delete() APE_OVERRIDE;

    // attributes
    int64 GetPosition() APE_OVERRIDE;
    int64 GetSize() APE_OVERRIDE;

private:
    CSmartPtr<unsigned char> m_spBuffer;
    HANDLE      m_hFile;
    str_utfn    m_cFileName[APE_MAX_PATH];
    bool        m_bReadOnly;
    bool        m_bPipe;
};

}

#endif //IO_USE_WIN_FILE_IO
