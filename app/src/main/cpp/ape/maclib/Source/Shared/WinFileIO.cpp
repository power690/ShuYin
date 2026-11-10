#include "All.h"

#ifdef IO_USE_WIN_FILE_IO

#include "WinFileIO.h"
#ifndef _UNICODE
#include "CharacterHelper.h"
#endif

namespace APE
{

CWinFileIO::CWinFileIO()
{
    m_hFile = INVALID_HANDLE_VALUE;
    APE_CLEAR(m_cFileName);
    m_bReadOnly = false;
    m_bPipe = false;
}

CWinFileIO::~CWinFileIO()
{
    APE_SAFE_FILE_CLOSE(m_hFile)
}

int CWinFileIO::Open(const str_utfn * pName, bool bOpenReadOnly)
{
    Close();

    size_t nNameLength = wcslen(pName);

    if (nNameLength >= APE_MAX_PATH)
        return ERROR_UNDEFINED;

    #ifdef _UNICODE
        str_utfn * pCopy = new str_utfn [nNameLength + 1];
        memcpy(pCopy, pName, sizeof(str_utfn) * nNameLength);
        pCopy[nNameLength] = 0;
        CSmartPtr<str_utfn> spName(pCopy, true);
    #else
        CSmartPtr<char> spName(CAPECharacterHelper::GetANSIFromUTFN(pName), true);
    #endif

    // handle pipes vs files
    if (0 == wcscmp(pName, L"-"))
    {
        // flag that we're a pipe
        m_bPipe = true;

        // pipes are read-only
        m_hFile = GetStdHandle(STD_INPUT_HANDLE);
        m_bReadOnly = true;
        if (m_hFile == INVALID_HANDLE_VALUE)
            return ERROR_INVALID_INPUT_FILE;
    }
    else
    {
        // open file (read / write)
        if (!bOpenReadOnly && (m_hFile == INVALID_HANDLE_VALUE))
            m_hFile = ::CreateFile(spName, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, APE_NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, APE_NULL);
        if (m_hFile == INVALID_HANDLE_VALUE)
        {
            // open file (read-only)
            m_hFile = ::CreateFile(spName, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, APE_NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, APE_NULL);
            if (m_hFile == INVALID_HANDLE_VALUE)
            {
                const DWORD dwError = GetLastError();
                if (dwError == ERROR_SHARING_VIOLATION)
                {
                    // file in use
                    return ERROR_OPENING_FILE_IN_USE;
                }
                else
                {
                    // dwError of 2 means not found
                    return ERROR_INVALID_INPUT_FILE;
                }
            }
            else
            {
                m_bReadOnly = true;
            }
        }
        else
        {
            m_bReadOnly = false;
        }
    }

    wcscpy_s(m_cFileName, APE_MAX_PATH, pName);

    return ERROR_SUCCESS;
}

int CWinFileIO::Close()
{
    APE_SAFE_FILE_CLOSE(m_hFile)

    return ERROR_SUCCESS;
}

int CWinFileIO::Read(void * pBuffer, int64 nBytesToRead, int64 * pBytesRead)
{
    bool bRetVal = true;

    int64 nTotalBytesRead = 0;
    int64 nBytesLeft = nBytesToRead;
    unsigned char * pucBuffer = static_cast<unsigned char *>(pBuffer);

    // error out if we try to read more than the Windows API allows with one call
    // we could also loop and read over and over, but nothing uses that today so we just error
    if (static_cast<unsigned long>(nBytesToRead) != nBytesToRead)
        return ERROR_UNDEFINED;

    // loop and read until we read all the data or have a failure
    while ((nBytesLeft > 0) && bRetVal)
    {
        unsigned long nBytesRead = 0;
        bRetVal = ::ReadFile(m_hFile, &pucBuffer[nBytesToRead - nBytesLeft], static_cast<DWORD>(nBytesLeft), &nBytesRead, APE_NULL) ? true : false;
        if (bRetVal && (nBytesRead <= 0))
            bRetVal = false;

        if (bRetVal)
        {
            nBytesLeft -= nBytesRead;
            nTotalBytesRead += nBytesRead;
        }
    }

    // succeed if we actually read data then fail next call
    if ((bRetVal == false) && (nTotalBytesRead > 0))
        bRetVal = true;

    // update the bytes read
    if (pBytesRead != APE_NULL)
        *pBytesRead = nTotalBytesRead;

    return bRetVal ? ERROR_SUCCESS : ERROR_IO_READ;
}

int CWinFileIO::Write(const void * pBuffer, int64 nBytesToWrite, int64 * pBytesWritten)
{
    // error out if we try to write more than the Windows API allows with one call
    // we could loop and write over and over, but nothing uses this today so we'll just error
    unsigned long nBytesToWriteCast = static_cast<unsigned long>(nBytesToWrite);
    if (nBytesToWriteCast != nBytesToWrite)
        return ERROR_UNDEFINED;

    unsigned long nBytesWritten = 0;
    const bool bRetVal = WriteFile(m_hFile, pBuffer, nBytesToWriteCast, &nBytesWritten, APE_NULL) ? true : false;
    if (pBytesWritten != APE_NULL)
        *pBytesWritten = static_cast<unsigned int>(nBytesWritten);
    if ((bRetVal == 0) || (nBytesWritten != nBytesToWrite))
        return ERROR_IO_WRITE;
    else
        return ERROR_SUCCESS;
}

int CWinFileIO::Seek(int64 nPosition, SeekMethod nMethod)
{
    DWORD dwMoveMethod = 0;
    if (nMethod == SeekFileBegin)
    {
        dwMoveMethod = FILE_BEGIN;
    }
    else if (nMethod == SeekFileEnd)
    {
        nPosition = -abs(nPosition);
        dwMoveMethod = FILE_END;
    }
    else if (nMethod == SeekFileCurrent)
    {
        dwMoveMethod = FILE_CURRENT;
    }

    const LONG Low = static_cast<LONG>(nPosition & 0xFFFFFFFF);
    LONG High = static_cast<LONG>(nPosition >> 32);

    DWORD dwResult = SetFilePointer(m_hFile, Low, &High, dwMoveMethod);

    return (dwResult == INVALID_SET_FILE_POINTER) ? ERROR_UNDEFINED : ERROR_SUCCESS;
}

int CWinFileIO::SetEOF()
{
    // set the file EOF
    return SetEndOfFile(m_hFile) ? ERROR_SUCCESS : ERROR_UNDEFINED;
}

int64 CWinFileIO::GetPosition()
{
    LONG nPositionHigh = 0;
    const DWORD dwPositionLow = SetFilePointer(m_hFile, 0, &nPositionHigh, FILE_CURRENT);
    const int64 nPosition = static_cast<int64>(dwPositionLow) + (static_cast<int64>(nPositionHigh) << 32);
    return nPosition;
}

int64 CWinFileIO::GetSize()
{
    if (m_bPipe)
        return APE_FILE_SIZE_UNDEFINED;

    DWORD dwFileSizeHigh = 0;
    const DWORD dwFileSizeLow = GetFileSize(m_hFile, &dwFileSizeHigh);
    return static_cast<int64>(dwFileSizeLow) + (static_cast<int64>(dwFileSizeHigh) << 32);
}

int CWinFileIO::Create(const str_utfn * pName)
{
    Close();

    size_t nNameLength = wcslen(pName);

    if (nNameLength >= APE_MAX_PATH)
        return ERROR_UNDEFINED;

    #ifdef _UNICODE
        str_utfn * pCopy = new str_utfn [nNameLength + 1];
        memcpy(pCopy, pName, sizeof(str_utfn) * nNameLength);
        pCopy[nNameLength] = 0;
        CSmartPtr<str_utfn> spName(pCopy, true);
    #else
        CSmartPtr<char> spName(CAPECharacterHelper::GetANSIFromUTFN(pName), true);
    #endif

    if (0 == wcscmp(pName, L"-"))
    {
        m_hFile = GetStdHandle(STD_OUTPUT_HANDLE);
        if (m_hFile == INVALID_HANDLE_VALUE)
            return ERROR_INVALID_OUTPUT_FILE;

        m_bReadOnly = false;
    }
    else
    {
        m_hFile = CreateFile(spName, GENERIC_WRITE | GENERIC_READ, 0, APE_NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, APE_NULL);
        if (m_hFile == INVALID_HANDLE_VALUE)
        {
            DWORD dwError = GetLastError();
            if (dwError == ERROR_ACCESS_DENIED)
                return ERROR_UAC_PERMISSION;
            else
                return ERROR_INVALID_OUTPUT_FILE;
        }

        m_bReadOnly = false;
    }

    wcscpy_s(m_cFileName, APE_MAX_PATH, pName);

    return ERROR_SUCCESS;
}

int CWinFileIO::Delete()
{
    Close();

    #ifdef _UNICODE
        CSmartPtr<str_utfn> spName(m_cFileName, true, false);
    #else
        CSmartPtr<char> spName(CAPECharacterHelper::GetANSIFromUTFN(m_cFileName), true);
    #endif

    SetFileAttributes(spName, FILE_ATTRIBUTE_NORMAL);
    return DeleteFile(spName) ? ERROR_SUCCESS : ERROR_UNDEFINED;
}

}

#endif // #ifdef IO_USE_WIN_FILE_IO
